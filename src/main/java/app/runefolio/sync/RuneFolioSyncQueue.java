package app.runefolio.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Durable outbox of sync events. Every entry is bound to a fingerprint of the connection
 * token that authorised its capture and can only ever be sent under that same token; the
 * binding is stored beside the event and never leaves the client.
 */
@Slf4j
final class RuneFolioSyncQueue
{
    private static final String CONFIG_GROUP = "runefolio";
    private static final String CONFIG_KEY = "syncQueue.v2";
    private static final String LEGACY_CONFIG_KEY = "syncQueue.v1";
    private static final String BINDING_PREFIX = "runefolio-sync-v1:";
    private static final int MAX_EVENTS = 1_000;
    private static final int MAX_QUEUE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 1024 * 1024;

    interface Storage
    {
        String get();

        void set(String value);

        default String getLegacy()
        {
            return null;
        }

        default void clearLegacy()
        {
        }
    }

    private final Storage storage;
    private final Supplier<Set<String>> deliverableBindings;
    // Entries own their event and encoded JSON. Neither callers nor upload snapshots
    // may mutate the cached representation after its byte budget has been checked.
    private final Map<String, Entry> events = new LinkedHashMap<>();

    private static final class Entry
    {
        private final RuneFolioSyncEvent event;
        private final String binding;
        private final String json;
        private final int bytes;

        private Entry(RuneFolioSyncEvent source, String binding)
        {
            JsonObject value = source.toJson();
            event = RuneFolioSyncEvent.fromJson(value);
            this.binding = binding;
            JsonObject stored = new JsonObject();
            stored.addProperty("b", binding);
            stored.add("e", value);
            json = stored.toString();
            bytes = json.getBytes(StandardCharsets.UTF_8).length + 1;
        }
    }

    RuneFolioSyncQueue(ConfigManager configManager, Supplier<Set<String>> deliverableBindings)
    {
        this(new Storage()
        {
            @Override
            public String get()
            {
                return configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
            }

            @Override
            public void set(String value)
            {
                configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, value);
            }

            @Override
            public String getLegacy()
            {
                return configManager.getConfiguration(CONFIG_GROUP, LEGACY_CONFIG_KEY);
            }

            @Override
            public void clearLegacy()
            {
                configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_CONFIG_KEY);
            }
        }, deliverableBindings);
    }

    RuneFolioSyncQueue(Storage storage, Supplier<Set<String>> deliverableBindings)
    {
        this.storage = storage;
        this.deliverableBindings = deliverableBindings;
        load();
    }

    /** Fingerprint of a connection token; distinct from the screenshot spool's so the two cannot be cross-matched. */
    static String binding(String token)
    {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing connection token");
        try
        {
            StringBuilder result = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest((BINDING_PREFIX + token).getBytes(StandardCharsets.UTF_8)))
            {
                result.append("0123456789abcdef".charAt((value & 255) >>> 4));
                result.append("0123456789abcdef".charAt(value & 15));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    synchronized boolean enqueue(RuneFolioSyncEvent event, String binding)
    {
        if (binding == null || binding.isBlank())
        {
            log.warn("RuneFolio sync event was captured without a connection; not queued");
            return false;
        }
        Entry entry = new Entry(event, binding);
        if (entry.bytes > MAX_BATCH_BYTES / 2)
        {
            log.warn("RuneFolio sync event exceeds the upload byte limit");
            return false;
        }
        Map<String, Entry> candidate = new LinkedHashMap<>(events);
        candidate.values().removeIf(existing -> binding.equals(existing.binding) && entry.event.supersedes(existing.event));
        candidate.put(entry.event.getId(), entry);
        if (overBudget(candidate))
        {
            evictUndeliverable(candidate, entry.event.getId());
        }
        if (overBudget(candidate))
        {
            log.warn("RuneFolio sync queue is full; refusing to discard an existing event");
            return false;
        }

        // Preserve synchronous durability, without re-encoding the existing backlog.
        // A failed storage write must not publish a replacement only in memory.
        persist(candidate);
        events.clear();
        events.putAll(candidate);
        return true;
    }

    private static boolean overBudget(Map<String, Entry> candidate)
    {
        return candidate.size() > MAX_EVENTS
            || candidate.values().stream().mapToLong(existing -> existing.bytes).sum() > MAX_QUEUE_BYTES;
    }

    /** Drops, oldest first, only entries bound to a connection this client no longer holds. */
    private void evictUndeliverable(Map<String, Entry> candidate, String incomingId)
    {
        Set<String> deliverable = deliverableBindings.get();
        int evicted = 0;
        for (Iterator<Map.Entry<String, Entry>> iterator = candidate.entrySet().iterator(); iterator.hasNext() && overBudget(candidate); )
        {
            Map.Entry<String, Entry> existing = iterator.next();
            if (!existing.getKey().equals(incomingId) && !deliverable.contains(existing.getValue().binding))
            {
                iterator.remove();
                evicted++;
            }
        }
        if (evicted > 0)
        {
            log.info("Dropped {} queued RuneFolio events that belonged to a connection this client no longer has", evicted);
        }
    }

    synchronized List<RuneFolioSyncEvent> snapshot(int limit, String binding, Predicate<RuneFolioSyncEvent> filter)
    {
        List<RuneFolioSyncEvent> result = new ArrayList<>();
        if (limit <= 0 || binding == null) return result;
        long bytes = 0;
        for (Entry entry : events.values())
        {
            if (binding.equals(entry.binding) && filter.test(entry.event))
            {
                int size = entry.bytes;
                if (bytes + size > MAX_BATCH_BYTES) break;
                result.add(RuneFolioSyncEvent.fromJson(entry.event.toJson()));
                bytes += size;
                if (result.size() >= limit)
                {
                    break;
                }
            }
        }
        return result;
    }

    synchronized void acknowledge(Collection<String> eventIds)
    {
        if (eventIds.isEmpty())
        {
            return;
        }
        Map<String, Entry> candidate = new LinkedHashMap<>(events);
        boolean changed = false;
        for (String id : eventIds) changed |= candidate.remove(id) != null;
        if (!changed) return;
        persist(candidate);
        events.clear();
        events.putAll(candidate);
    }

    synchronized int size()
    {
        return events.size();
    }

    private void load()
    {
        discardLegacyQueue();
        String saved = storage.get();
        if (saved == null || saved.isBlank())
        {
            return;
        }

        // The saved queue holds character data, so a corrupt value is never echoed into the log.
        try
        {
            JsonElement parsed = new JsonParser().parse(saved);
            if (!parsed.isJsonArray())
            {
                log.warn("Saved RuneFolio sync queue was not a JSON array; discarding it");
            }
            else
            {
                for (JsonElement element : parsed.getAsJsonArray())
                {
                    try
                    {
                        JsonObject stored = element.getAsJsonObject();
                        String binding = stored.get("b").getAsString();
                        if (binding.isBlank()) throw new IllegalArgumentException("blank binding");
                        RuneFolioSyncEvent event = RuneFolioSyncEvent.fromJson(stored.getAsJsonObject("e"));
                        events.put(event.getId(), new Entry(event, binding));
                    }
                    catch (RuntimeException exception)
                    {
                        log.warn("Skipping an unreadable queued RuneFolio event ({})", exception.getClass().getSimpleName());
                    }
                }
            }
        }
        catch (RuntimeException exception)
        {
            log.warn("Unable to read the saved RuneFolio sync queue ({})", exception.getClass().getSimpleName());
        }
        persist(events);
    }

    /** Events saved before bindings existed cannot prove which connection authorised them, so they are never sent. */
    private void discardLegacyQueue()
    {
        String legacy = storage.getLegacy();
        if (legacy == null || legacy.isBlank())
        {
            return;
        }
        int count = 0;
        try
        {
            JsonElement parsed = new JsonParser().parse(legacy);
            if (parsed.isJsonArray()) count = parsed.getAsJsonArray().size();
        }
        catch (RuntimeException ignored)
        {
            // Unreadable legacy data is discarded the same way.
        }
        log.warn("Discarding {} queued RuneFolio events saved by an earlier plugin version; the next full sync replaces snapshots", count);
        storage.clearLegacy();
    }

    private void persist(Map<String, Entry> entries)
    {
        StringBuilder json = new StringBuilder();
        json.append('[');
        for (Entry entry : entries.values())
        {
            if (json.length() > 1) json.append(',');
            json.append(entry.json);
        }
        json.append(']');
        storage.set(json.toString());
    }
}
