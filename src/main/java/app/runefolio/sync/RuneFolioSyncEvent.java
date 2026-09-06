package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class RuneFolioSyncEvent
{
    static final String SKILL_SNAPSHOT_TYPE = "skills.snapshot";
    static final String QUEST_SNAPSHOT_TYPE = "quests.snapshot";
    static final String DIARY_SNAPSHOT_TYPE = "diaries.snapshot";
    static final String COMBAT_SNAPSHOT_TYPE = "combat_achievements.snapshot";
    static final String COLLECTION_CATEGORY_TYPE = "collection_log.category";
    static final String LOOT_DROP_TYPE = "loot.drop";
    static final String COMBAT_UNLOCK_TYPE = "combat_achievement.unlock";
    static final String COLLECTION_UNLOCK_TYPE = "collection_log.unlock";
    static final int PAYLOAD_VERSION = 1;
    private static final Set<String> SUPERSEDING_SNAPSHOT_TYPES = Set.of(
        SKILL_SNAPSHOT_TYPE,
        QUEST_SNAPSHOT_TYPE,
        DIARY_SNAPSHOT_TYPE,
        COMBAT_SNAPSHOT_TYPE,
        COLLECTION_CATEGORY_TYPE
    );

    private final String id;
    private final String type;
    private final int version;
    private final String occurredAt;
    private final String characterName;
    private final JsonObject payload;

    private RuneFolioSyncEvent(
        String id,
        String type,
        int version,
        String occurredAt,
        String characterName,
        JsonObject payload
    )
    {
        this.id = id;
        this.type = type;
        this.version = version;
        this.occurredAt = occurredAt;
        this.characterName = characterName;
        this.payload = payload;
    }

    static RuneFolioSyncEvent skillSnapshot(
        String characterName,
        String trigger,
        List<RuneFolioApiClient.SkillSnapshot> skills
    )
    {
        JsonArray skillArray = new JsonArray();
        for (RuneFolioApiClient.SkillSnapshot skill : skills)
        {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", skill.getName());
            entry.addProperty("level", skill.getLevel());
            entry.addProperty("experience", skill.getExperience());
            skillArray.add(entry);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("trigger", trigger);
        payload.add("skills", skillArray);
        return new RuneFolioSyncEvent(
            UUID.randomUUID().toString(),
            SKILL_SNAPSHOT_TYPE,
            PAYLOAD_VERSION,
            Instant.now().toString(),
            characterName,
            payload
        );
    }

    static RuneFolioSyncEvent progressSnapshot(
        String type,
        String characterName,
        String trigger,
        JsonObject state
    )
    {
        if (!SUPERSEDING_SNAPSHOT_TYPES.contains(type) || SKILL_SNAPSHOT_TYPE.equals(type))
        {
            throw new IllegalArgumentException("Unsupported RuneFolio progress snapshot type.");
        }
        JsonObject payload = state.deepCopy();
        payload.addProperty("trigger", trigger);
        return create(type, characterName, payload);
    }

    static RuneFolioSyncEvent combatAchievementUnlock(
        String characterName,
        String tier,
        String taskName,
        Integer points
    )
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("tier", tier);
        payload.addProperty("taskName", taskName);
        if (points != null)
        {
            payload.addProperty("points", points);
        }
        return create(COMBAT_UNLOCK_TYPE, characterName, payload);
    }

    static RuneFolioSyncEvent collectionLogUnlock(String characterName, String itemName)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemName", itemName);
        return create(COLLECTION_UNLOCK_TYPE, characterName, payload);
    }

    static RuneFolioSyncEvent lootDrop(
        String characterName,
        String sourceName,
        String sourceType,
        int combatLevel,
        int eventCount,
        JsonArray items,
        long totalGeValue,
        long totalHaValue
    )
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("trigger", "event");
        payload.addProperty("sourceName", sourceName);
        payload.addProperty("sourceType", sourceType);
        payload.addProperty("combatLevel", combatLevel);
        payload.addProperty("eventCount", eventCount);
        payload.add("items", items.deepCopy());
        payload.addProperty("totalGeValue", totalGeValue);
        payload.addProperty("totalHaValue", totalHaValue);
        return create(LOOT_DROP_TYPE, characterName, payload);
    }

    private static RuneFolioSyncEvent create(String type, String characterName, JsonObject payload)
    {
        return new RuneFolioSyncEvent(
            UUID.randomUUID().toString(),
            type,
            PAYLOAD_VERSION,
            Instant.now().toString(),
            characterName,
            payload
        );
    }

    static RuneFolioSyncEvent fromJson(JsonObject json)
    {
        String id = requiredString(json, "id");
        UUID.fromString(id);
        String type = requiredString(json, "type");
        int version = json.get("version").getAsInt();
        String occurredAt = requiredString(json, "occurredAt");
        Instant.parse(occurredAt);
        String characterName = requiredString(json, "characterName");
        JsonElement payload = json.get("payload");
        if (version < 1 || characterName.isBlank() || payload == null || !payload.isJsonObject())
        {
            throw new IllegalArgumentException("Invalid queued RuneFolio event.");
        }
        return new RuneFolioSyncEvent(id, type, version, occurredAt, characterName, payload.getAsJsonObject());
    }

    JsonObject toJson()
    {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", type);
        json.addProperty("version", version);
        json.addProperty("occurredAt", occurredAt);
        json.addProperty("characterName", characterName);
        json.add("payload", payload.deepCopy());
        return json;
    }

    boolean supersedes(RuneFolioSyncEvent other)
    {
        if (!SUPERSEDING_SNAPSHOT_TYPES.contains(type))
        {
            return false;
        }
        if (!type.equals(other.type) || !normalise(characterName).equals(normalise(other.characterName)))
        {
            return false;
        }
        if (COLLECTION_CATEGORY_TYPE.equals(type))
        {
            return payloadString("category").equalsIgnoreCase(other.payloadString("category"));
        }
        return true;
    }

    String getId()
    {
        return id;
    }

    String getCharacterName()
    {
        return characterName;
    }

    String getTrigger()
    {
        return payloadString("trigger");
    }

    private String payloadString(String key)
    {
        JsonElement value = payload.get(key);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static String requiredString(JsonObject json, String key)
    {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
        {
            throw new IllegalArgumentException("Missing queued RuneFolio event field: " + key);
        }
        return value.getAsString();
    }

    private static String normalise(String value)
    {
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
