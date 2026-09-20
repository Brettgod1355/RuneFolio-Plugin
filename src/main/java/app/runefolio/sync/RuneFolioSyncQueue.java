package app.runefolio.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
 *
 * <p>Each event occupies its own config key. That matters because RuneLite's ConfigManager
 * only writes to disk periodically, and its {@code ConfigData.patch} merges the profile file
 * per key: two clients sharing one profile each rewrite only the keys they touched, so neither
 * can discard events the other queued. An earlier layout kept the whole queue in a single
 * {@code syncQueue.v2} value, where that per-key merge gave no protection at all and the last
 * client to flush silently dropped the other's events.</p>
 */
@Slf4j
final class RuneFolioSyncQueue
{
    private static final String CONFIG_GROUP = "runefolio";
    private static final String KEY_PREFIX = "syncQueue.v3.";
    private static final String BLOB_CONFIG_KEY = "syncQueue.v2";
    private static final String LEGACY_CONFIG_KEY = "syncQueue.v1";
    private static final String BINDING_PREFIX = "runefolio-sync-v1:";
    private static final int MAX_EVENTS = 1_000;
    private static final int MAX_QUEUE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 1024 * 1024;

    interface Storage
    {
        /** Every stored entry, keyed by event id. */
        Map<String, String> load();

        void put(String eventId, String json);

        void remove(String eventId);

        /** The single-value queue written by 0.3.58 and earlier, migrated on first load. */
        default String getBlob()
        {
            return null;
        }

        default void clearBlob()
        {
        }

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
    // Queue order has to be stored, because the config store hands keys back unordered.
    private long nextSequence;

    private static final class Entry
    {
        private final RuneFolioSyncEvent event;
        private final String binding;
        private final long sequence;
        private final String json;
        private final int bytes;

        private Entry(RuneFolioSyncEvent source, String binding, long sequence)
        {
            JsonObject value = source.toJson();
            event = RuneFolioSyncEvent.fromJson(value);
            this.binding = binding;
            this.sequence = sequence;
            JsonObject stored = new JsonObject();
            stored.addProperty("s", sequence);
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
            public Map<String, String> load()
            {
                Map<String, String> stored = new LinkedHashMap<>();
                for (String key : configManager.getConfigurationKeys(CONFIG_GROUP + "." + KEY_PREFIX))
                {
                    String relative = key.substring(CONFIG_GROUP.length() + 1);
                    String value = configManager.getConfiguration(CONFIG_GROUP, relative);
                    if (value != null && !value.isBlank())
                    {
                        stored.put(relative.substring(KEY_PREFIX.length()), value);
                    }
                }
                return stored;
            }

            @Override
            public void put(String eventId, String json)
            {
                configManager.setConfiguration(CONFIG_GROUP, KEY_PREFIX + eventId, json);
            }

            @Override
            public void remove(String eventId)
            {
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_PREFIX + eventId);
            }

            @Override
            public String getBlob()
            {
                return configManager.getConfiguration(CONFIG_GROUP, BLOB_CONFIG_KEY);
            }

            @Override
            public void clearBlob()
            {
                configManager.unsetConfiguration(CONFIG_GROUP, BLOB_CONFIG_KEY);
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

    /** An event id becomes part of a config key, so anything that could not round-trip is rejected. */
    private static boolean storableId(String id)
    {
        return id != null && id.matches("[A-Za-z0-9-]{1,64}");
    }

    synchronized boolean enqueue(RuneFolioSyncEvent event, String binding)
    {
        if (binding == null || binding.isBlank())
        {
            log.warn("RuneFolio sync event was captured without a connection; not queued");
            return false;
        }
        Entry entry = new Entry(event, binding, nextSequence);
        if (!storableId(entry.event.getId()))
        {
            log.warn("RuneFolio sync event has an unusable id; not queued");
            return false;
        }
        if (entry.bytes > MAX_BATCH_BYTES / 2)
        {
            log.warn("RuneFolio sync event exceeds the upload byte limit");
            return false;
        }
        Map<String, Entry> candidate = new LinkedHashMap<>(events);
        List<String> discarded = new ArrayList<>();
        for (Iterator<Map.Entry<String, Entry>> iterator = candidate.entrySet().iterator(); iterator.hasNext(); )
        {
            Map.Entry<String, Entry> existing = iterator.next();
            if (binding.equals(existing.getValue().binding) && entry.event.supersedes(existing.getValue().event))
            {
                discarded.add(existing.getKey());
                iterator.remove();
            }
        }
        candidate.put(entry.event.getId(), entry);
        if (overBudget(candidate))
        {
            evictUndeliverable(candidate, entry.event.getId(), discarded);
        }
        if (overBudget(candidate))
        {
            log.warn("RuneFolio sync queue is full; refusing to discard an existing event");
            return false;
        }

        // The new entry is written before anything is deleted, so a storage failure can never
        // lose an event: the worst case is a superseded entry surviving and being replaced again.
        storage.put(entry.event.getId(), entry.json);
        for (String gone : discarded)
        {
            storage.remove(gone);
        }
        events.clear();
        events.putAll(candidate);
        nextSequence++;
        return true;
    }

    private static boolean overBudget(Map<String, Entry> candidate)
    {
        return candidate.size() > MAX_EVENTS
            || candidate.values().stream().mapToLong(existing -> existing.bytes).sum() > MAX_QUEUE_BYTES;
    }

    /** Drops, oldest first, only entries bound to a connection this client no longer holds. */
    private void evictUndeliverable(Map<String, Entry> candidate, String incomingId, Collection<String> discarded)
    {
        Set<String> deliverable = deliverableBindings.get();
        int evicted = 0;
        for (Iterator<Map.Entry<String, Entry>> iterator = candidate.entrySet().iterator(); iterator.hasNext() && overBudget(candidate); )
        {
            Map.Entry<String, Entry> existing = iterator.next();
            if (!existing.getKey().equals(incomingId) && !deliverable.contains(existing.getValue().binding))
            {
                iterator.remove();
                discarded.add(existing.getKey());
                evicted++;
            }
        }
        if (evicted > 0)
        {
            log.info("Dropped {} queued RuneFolio events that belonged to a connection this client no longer has", evicted);
        }
    }

    /**
     * Removes entries whose connection no longer exists in this profile. Such an entry can never be
     * sent, because delivery requires the exact token that recorded it, so leaving it queued only
     * inflates the pending count forever. Waiting for the queue to fill up before evicting it meant
     * a reconnected character's old events were stranded indefinitely.
     */
    synchronized int pruneUndeliverable()
    {
        Set<String> deliverable = deliverableBindings.get();
        if (deliverable.isEmpty())
        {
            // No connection at all is a transient state, not proof that every entry is dead.
            return 0;
        }
        List<String> stranded = new ArrayList<>();
        for (Map.Entry<String, Entry> existing : events.entrySet())
        {
            if (!deliverable.contains(existing.getValue().binding))
            {
                stranded.add(existing.getKey());
            }
        }
        for (String id : stranded)
        {
            storage.remove(id);
            events.remove(id);
        }
        if (!stranded.isEmpty())
        {
            log.info("Dropped {} queued RuneFolio events whose connection no longer exists in this profile", stranded.size());
        }
        return stranded.size();
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
        List<String> removed = new ArrayList<>();
        for (String id : eventIds)
        {
            if (candidate.remove(id) != null) removed.add(id);
        }
        if (removed.isEmpty()) return;
        for (String id : removed)
        {
            storage.remove(id);
        }
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
        migrateBlobQueue();

        // The saved queue holds character data, so a corrupt value is never echoed into the log.
        List<Entry> loaded = new ArrayList<>();
        for (Map.Entry<String, String> stored : storage.load().entrySet())
        {
            try
            {
                Entry entry = decode(stored.getValue());
                if (!stored.getKey().equals(entry.event.getId()))
                {
                    throw new IllegalArgumentException("id does not match its key");
                }
                loaded.add(entry);
            }
            catch (RuntimeException exception)
            {
                log.warn("Discarding an unreadable queued RuneFolio event ({})", exception.getClass().getSimpleName());
                storage.remove(stored.getKey());
            }
        }
        loaded.sort(Comparator.comparingLong((Entry entry) -> entry.sequence).thenComparing(entry -> entry.event.getId()));
        for (Entry entry : loaded)
        {
            events.put(entry.event.getId(), entry);
            nextSequence = Math.max(nextSequence, entry.sequence + 1);
        }
        pruneUndeliverable();
    }

    private Entry decode(String value)
    {
        JsonObject stored = new JsonParser().parse(value).getAsJsonObject();
        String binding = stored.get("b").getAsString();
        if (binding.isBlank()) throw new IllegalArgumentException("blank binding");
        long sequence = stored.has("s") ? stored.get("s").getAsLong() : 0L;
        RuneFolioSyncEvent event = RuneFolioSyncEvent.fromJson(stored.getAsJsonObject("e"));
        if (!storableId(event.getId())) throw new IllegalArgumentException("unusable id");
        return new Entry(event, binding, sequence);
    }

    /**
     * Splits the single-value queue into one key per event. The old value is cleared only once every
     * entry has been written, so an interrupted migration retries instead of losing the backlog.
     */
    private void migrateBlobQueue()
    {
        String blob = storage.getBlob();
        if (blob == null || blob.isBlank())
        {
            return;
        }
        int migrated = 0;
        int unreadable = 0;
        try
        {
            JsonElement parsed = new JsonParser().parse(blob);
            if (!parsed.isJsonArray())
            {
                log.warn("Saved RuneFolio sync queue was not a JSON array; discarding it");
                storage.clearBlob();
                return;
            }
            long sequence = 0;
            for (JsonElement element : parsed.getAsJsonArray())
            {
                try
                {
                    JsonObject stored = element.getAsJsonObject();
                    String binding = stored.get("b").getAsString();
                    if (binding.isBlank()) throw new IllegalArgumentException("blank binding");
                    RuneFolioSyncEvent event = RuneFolioSyncEvent.fromJson(stored.getAsJsonObject("e"));
                    if (!storableId(event.getId())) throw new IllegalArgumentException("unusable id");
                    Entry entry = new Entry(event, binding, sequence++);
                    storage.put(entry.event.getId(), entry.json);
                    migrated++;
                }
                catch (RuntimeException exception)
                {
                    unreadable++;
                    log.warn("Skipping an unreadable queued RuneFolio event ({})", exception.getClass().getSimpleName());
                }
            }
        }
        catch (RuntimeException exception)
        {
            log.warn("Unable to read the saved RuneFolio sync queue ({})", exception.getClass().getSimpleName());
            storage.clearBlob();
            return;
        }
        storage.clearBlob();
        log.info("Moved {} queued RuneFolio events onto their own keys so two clients cannot overwrite each other ({} unreadable)",
            migrated, unreadable);
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
}
