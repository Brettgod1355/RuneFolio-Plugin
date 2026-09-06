package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

    interface Storage
    {
        String get();

        void set(String value);
    }

    private final Storage storage;
    private final Map<String, RuneFolioSyncEvent> events = new LinkedHashMap<>();

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
        events.values().removeIf(event::supersedes);
        if (events.size() >= MAX_EVENTS)
        {
            log.warn("RuneFolio sync queue is full; refusing to discard an existing event");
            persist();
            return false;
        }

        events.put(event.getId(), event);
        persist();
        return true;
    }

    synchronized List<RuneFolioSyncEvent> snapshot(int limit, Predicate<RuneFolioSyncEvent> filter)
    {
        List<RuneFolioSyncEvent> result = new ArrayList<>();
        for (RuneFolioSyncEvent event : events.values())
        {
            if (filter.test(event))
            {
                result.add(event);
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
        eventIds.forEach(events::remove);
        persist();
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

        try
        {
            JsonArray array = new JsonParser().parse(saved).getAsJsonArray();
            for (JsonElement element : array)
            {
                try
                {
                    RuneFolioSyncEvent event = RuneFolioSyncEvent.fromJson(element.getAsJsonObject());
                    events.put(event.getId(), event);
                }
                catch (RuntimeException exception)
                {
                    log.warn("Skipping an unreadable queued RuneFolio event", exception);
                }
            }
        }
        catch (RuntimeException exception)
        {
            log.warn("Unable to read the saved RuneFolio sync queue", exception);
        }
        persist();
    }

    private void persist()
    {
        JsonArray array = new JsonArray();
        events.values().forEach(event -> array.add(event.toJson()));
        storage.set(array.toString());
    }
}
