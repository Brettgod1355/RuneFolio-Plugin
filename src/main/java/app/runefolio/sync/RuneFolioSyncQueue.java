package app.runefolio.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
final class RuneFolioSyncQueue
{
    private static final String CONFIG_GROUP = "runefolio";
    private static final String CONFIG_KEY = "syncQueue.v1";
    private static final int MAX_EVENTS = 1_000;
    private static final int MAX_QUEUE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 1024 * 1024;

    interface Storage
    {
        String get();

        void set(String value);
    }

    private final Storage storage;
    // Entries own their event and encoded JSON. Neither callers nor upload snapshots
    // may mutate the cached representation after its byte budget has been checked.
    private final Map<String, Entry> events = new LinkedHashMap<>();

    private static final class Entry
    {
        private final RuneFolioSyncEvent event;
        private final String json;
        private final int bytes;

        private Entry(RuneFolioSyncEvent source)
        {
            JsonObject value = source.toJson();
            event = RuneFolioSyncEvent.fromJson(value);
            json = value.toString();
            bytes = json.getBytes(StandardCharsets.UTF_8).length + 1;
        }
    }

    RuneFolioSyncQueue(ConfigManager configManager)
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
        });
    }

    RuneFolioSyncQueue(Storage storage)
    {
        this.storage = storage;
        load();
    }

    synchronized boolean enqueue(RuneFolioSyncEvent event)
    {
        Entry entry = new Entry(event);
        if (entry.bytes > MAX_BATCH_BYTES / 2)
        {
            log.warn("RuneFolio sync event exceeds the upload byte limit");
            return false;
        }
        Map<String, Entry> candidate = new LinkedHashMap<>(events);
        candidate.values().removeIf(existing -> entry.event.supersedes(existing.event));
        candidate.put(entry.event.getId(), entry);
        if (candidate.size() > MAX_EVENTS
            || candidate.values().stream().mapToLong(existing -> existing.bytes).sum() > MAX_QUEUE_BYTES)
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

    synchronized List<RuneFolioSyncEvent> snapshot(int limit, Predicate<RuneFolioSyncEvent> filter)
    {
        List<RuneFolioSyncEvent> result = new ArrayList<>();
        if (limit <= 0) return result;
        long bytes = 0;
        for (Entry entry : events.values())
        {
            if (filter.test(entry.event))
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
                    if (!element.isJsonObject())
                    {
                        log.warn("Skipping a queued RuneFolio event that is not a JSON object");
                        continue;
                    }
                    try
                    {
                        RuneFolioSyncEvent event = RuneFolioSyncEvent.fromJson(element.getAsJsonObject());
                        events.put(event.getId(), new Entry(event));
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
