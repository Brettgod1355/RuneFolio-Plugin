package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

final class RuneFolioProgressCollector
{
    // RuneLite's generated VarbitID names omit the three legacy Karamja completion
    // varbits, but the public API still exposes them in net.runelite.api.Varbits.
    private static final int KARAMJA_DIARY_EASY_COMPLETE = 3578;
    private static final int KARAMJA_DIARY_MEDIUM_COMPLETE = 3599;
    private static final int KARAMJA_DIARY_HARD_COMPLETE = 3611;

    private static final Set<Quest> SUPPLEMENTAL_QUESTS = EnumSet.of(
        Quest.ALFRED_GRIMHANDS_BARCRAWL,
        Quest.BARBARIAN_TRAINING,
        Quest.BEAR_YOUR_SOUL,
        Quest.CURSE_OF_THE_EMPTY_LORD,
        Quest.DADDYS_HOME,
        Quest.ENTER_THE_ABYSS,
        Quest.FAMILY_PEST,
        Quest.THE_FROZEN_DOOR,
        Quest.HIS_FAITHFUL_SERVANTS,
        Quest.HOPESPEARS_WILL,
        Quest.IN_SEARCH_OF_KNOWLEDGE,
        Quest.INTO_THE_TOMBS,
        Quest.LAIR_OF_TARN_RAZORLOR,
        Quest.MAGE_ARENA_I,
        Quest.MAGE_ARENA_II,
        Quest.SKIPPY_AND_THE_MOGRES,
        Quest.THE_ENCHANTED_KEY,
        Quest.THE_GENERALS_SHADOW,
        Quest.VALE_TOTEMS,
        Quest.RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST,
        Quest.RECIPE_FOR_DISASTER__MOUNTAIN_DWARF,
        Quest.RECIPE_FOR_DISASTER__WARTFACE__BENTNOZE,
        Quest.RECIPE_FOR_DISASTER__PIRATE_PETE,
        Quest.RECIPE_FOR_DISASTER__LUMBRIDGE_GUIDE,
        Quest.RECIPE_FOR_DISASTER__EVIL_DAVE,
        Quest.RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE,
        Quest.RECIPE_FOR_DISASTER__SIR_AMIK_VARZE,
        Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI,
        Quest.RECIPE_FOR_DISASTER__CULINAROMANCER
    );

    private static final DiaryTier[] DIARY_TIERS = new DiaryTier[]
    {
        new DiaryTier("ardougne", "easy", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE),
        new DiaryTier("ardougne", "medium", VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("ardougne", "hard", VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE),
        new DiaryTier("ardougne", "elite", VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE),
        new DiaryTier("desert", "easy", VarbitID.DESERT_DIARY_EASY_COMPLETE),
        new DiaryTier("desert", "medium", VarbitID.DESERT_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("desert", "hard", VarbitID.DESERT_DIARY_HARD_COMPLETE),
        new DiaryTier("desert", "elite", VarbitID.DESERT_DIARY_ELITE_COMPLETE),
        new DiaryTier("falador", "easy", VarbitID.FALADOR_DIARY_EASY_COMPLETE),
        new DiaryTier("falador", "medium", VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("falador", "hard", VarbitID.FALADOR_DIARY_HARD_COMPLETE),
        new DiaryTier("falador", "elite", VarbitID.FALADOR_DIARY_ELITE_COMPLETE),
        new DiaryTier("fremennik", "easy", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE),
        new DiaryTier("fremennik", "medium", VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("fremennik", "hard", VarbitID.FREMENNIK_DIARY_HARD_COMPLETE),
        new DiaryTier("fremennik", "elite", VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE),
        new DiaryTier("kandarin", "easy", VarbitID.KANDARIN_DIARY_EASY_COMPLETE),
        new DiaryTier("kandarin", "medium", VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("kandarin", "hard", VarbitID.KANDARIN_DIARY_HARD_COMPLETE),
        new DiaryTier("kandarin", "elite", VarbitID.KANDARIN_DIARY_ELITE_COMPLETE),
        new DiaryTier("karamja", "easy", KARAMJA_DIARY_EASY_COMPLETE),
        new DiaryTier("karamja", "medium", KARAMJA_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("karamja", "hard", KARAMJA_DIARY_HARD_COMPLETE),
        new DiaryTier("karamja", "elite", VarbitID.KARAMJA_DIARY_ELITE_COMPLETE),
        new DiaryTier("kourend_kebos", "easy", VarbitID.KOUREND_DIARY_EASY_COMPLETE),
        new DiaryTier("kourend_kebos", "medium", VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("kourend_kebos", "hard", VarbitID.KOUREND_DIARY_HARD_COMPLETE),
        new DiaryTier("kourend_kebos", "elite", VarbitID.KOUREND_DIARY_ELITE_COMPLETE),
        new DiaryTier("lumbridge_draynor", "easy", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE),
        new DiaryTier("lumbridge_draynor", "medium", VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("lumbridge_draynor", "hard", VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE),
        new DiaryTier("lumbridge_draynor", "elite", VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE),
        new DiaryTier("morytania", "easy", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE),
        new DiaryTier("morytania", "medium", VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("morytania", "hard", VarbitID.MORYTANIA_DIARY_HARD_COMPLETE),
        new DiaryTier("morytania", "elite", VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE),
        new DiaryTier("varrock", "easy", VarbitID.VARROCK_DIARY_EASY_COMPLETE),
        new DiaryTier("varrock", "medium", VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("varrock", "hard", VarbitID.VARROCK_DIARY_HARD_COMPLETE),
        new DiaryTier("varrock", "elite", VarbitID.VARROCK_DIARY_ELITE_COMPLETE),
        new DiaryTier("western_provinces", "easy", VarbitID.WESTERN_DIARY_EASY_COMPLETE),
        new DiaryTier("western_provinces", "medium", VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("western_provinces", "hard", VarbitID.WESTERN_DIARY_HARD_COMPLETE),
        new DiaryTier("western_provinces", "elite", VarbitID.WESTERN_DIARY_ELITE_COMPLETE),
        new DiaryTier("wilderness", "easy", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE),
        new DiaryTier("wilderness", "medium", VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE),
        new DiaryTier("wilderness", "hard", VarbitID.WILDERNESS_DIARY_HARD_COMPLETE),
        new DiaryTier("wilderness", "elite", VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE)
    };

    private RuneFolioProgressCollector()
    {
    }

    static int supplementalQuestCount()
    {
        return SUPPLEMENTAL_QUESTS.size();
    }

    static int trackedDiaryTierCount()
    {
        return DIARY_TIERS.length;
    }

    static JsonObject quests(Client client)
    {
        JsonArray quests = new JsonArray();
        int mainQuestCompleted = 0;
        int mainQuestTotal = 0;
        for (Quest quest : Quest.values())
        {
            String questState = quest.getState(client).name().toLowerCase(Locale.ROOT);
            boolean countsTowardQuestCape = !SUPPLEMENTAL_QUESTS.contains(quest);
            JsonObject entry = new JsonObject();
            entry.addProperty("key", quest.name().toLowerCase(Locale.ROOT));
            entry.addProperty("name", quest.getName());
            entry.addProperty("state", questState);
            entry.addProperty("countsTowardQuestCape", countsTowardQuestCape);
            quests.add(entry);
            if (countsTowardQuestCape)
            {
                mainQuestTotal++;
                if ("finished".equals(questState))
                {
                    mainQuestCompleted++;
                }
            }
        }

        JsonObject state = new JsonObject();
        state.add("quests", quests);
        state.addProperty("questPoints", client.getVarpValue(VarPlayer.QUEST_POINTS));
        state.addProperty("mainQuestCompleted", mainQuestCompleted);
        state.addProperty("mainQuestTotal", mainQuestTotal);
        state.addProperty("supplementalQuestEntries", SUPPLEMENTAL_QUESTS.size());
        return state;
    }

    static JsonObject diaries(Client client, JsonArray taskAreas)
    {
        JsonArray completedTiers = new JsonArray();
        for (DiaryTier diaryTier : DIARY_TIERS)
        {
            if ("karamja".equals(diaryTier.area) && !"elite".equals(diaryTier.tier))
            {
                continue;
            }
            if (client.getVarbitValue(diaryTier.varbit) > 0)
            {
                completedTiers.add(diaryTier.area + "." + diaryTier.tier);
            }
        }

        boolean karamjaCompletionValidated = false;
        if (taskAreas != null)
        {
            for (JsonElement rawArea : taskAreas)
            {
                if (!rawArea.isJsonObject())
                {
                    continue;
                }
                JsonObject area = rawArea.getAsJsonObject();
                String areaKey = string(area, "area");
                JsonArray tiers = array(area, "tiers");
                if ("karamja".equals(areaKey))
                {
                    karamjaCompletionValidated = tiers != null && tiers.size() == 4;
                }
                if (tiers == null)
                {
                    continue;
                }
                for (JsonElement rawTier : tiers)
                {
                    if (!rawTier.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject tier = rawTier.getAsJsonObject();
                    String tierKey = string(tier, "tier");
                    JsonArray tasks = array(tier, "tasks");
                    if (areaKey == null || tierKey == null || tasks == null || tasks.size() == 0)
                    {
                        continue;
                    }
                    if (allTasksCompleted(tasks))
                    {
                        addUnique(completedTiers, areaKey + "." + tierKey);
                    }
                }
            }
        }

        JsonObject state = new JsonObject();
        state.add("completedTiers", completedTiers);
        state.addProperty("trackedTierCount", DIARY_TIERS.length);
        state.addProperty("coverage", "all 48 tier states plus every task from diary areas opened in game");
        state.addProperty("karamjaCompletionValidated", karamjaCompletionValidated);
        state.add("taskAreas", taskAreas == null ? new JsonArray() : taskAreas.deepCopy());
        return state;
    }

    private static boolean allTasksCompleted(JsonArray tasks)
    {
        for (JsonElement rawTask : tasks)
        {
            if (!rawTask.isJsonObject()
                || !rawTask.getAsJsonObject().has("completed")
                || !rawTask.getAsJsonObject().get("completed").getAsBoolean())
            {
                return false;
            }
        }
        return true;
    }

    private static String string(JsonObject object, String key)
    {
        return object.has(key) && object.get(key).isJsonPrimitive()
            ? object.get(key).getAsString()
            : null;
    }

    private static JsonArray array(JsonObject object, String key)
    {
        return object.has(key) && object.get(key).isJsonArray()
            ? object.getAsJsonArray(key)
            : null;
    }

    private static void addUnique(JsonArray values, String value)
    {
        for (JsonElement existing : values)
        {
            if (existing.isJsonPrimitive() && value.equals(existing.getAsString()))
            {
                return;
            }
        }
        values.add(value);
    }

    static JsonObject combatAchievements(Client client)
    {
        JsonObject totals = new JsonObject();
        totals.addProperty("easy", client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY));
        totals.addProperty("medium", client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM));
        totals.addProperty("hard", client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD));
        totals.addProperty("elite", client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE));
        totals.addProperty("master", client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER));
        totals.addProperty("grandmaster", client.getVarbitValue(VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER));

        JsonObject state = new JsonObject();
        state.add("completedByTier", totals);
        state.add("completedTaskKeys", RuneFolioCombatTaskCatalog.completedTaskKeys(client));
        state.add("tasks", RuneFolioCombatTaskCatalog.taskStates(client));
        state.addProperty("trackedTaskCount", RuneFolioCombatTaskCatalog.trackedTaskCount());
        return state;
    }

    static JsonObject collectionLogCategory(Client client, ItemManager itemManager)
    {
        Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
        Widget items = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
        if (header == null || items == null || header.getChildren() == null || items.getChildren() == null
            || header.getChildren().length == 0 || header.getChildren()[0] == null)
        {
            return null;
        }

        String category = Text.removeTags(header.getChildren()[0].getText()).trim();
        if (category.isEmpty())
        {
            return null;
        }

        JsonArray entries = new JsonArray();
        int obtainedCount = 0;
        for (Widget child : items.getChildren())
        {
            if (child == null || child.getItemId() <= 0)
            {
                continue;
            }

            boolean obtained = child.getOpacity() == 0;
            JsonObject entry = new JsonObject();
            entry.addProperty("itemId", child.getItemId());
            entry.addProperty("itemName", itemManager.getItemComposition(child.getItemId()).getName());
            entry.addProperty("obtained", obtained);
            entry.addProperty("quantity", obtained ? Math.max(1, child.getItemQuantity()) : 0);
            entries.add(entry);
            if (obtained)
            {
                obtainedCount++;
            }
        }

        if (entries.size() == 0)
        {
            return null;
        }

        JsonObject state = new JsonObject();
        state.addProperty("category", category);
        state.addProperty("obtainedCount", obtainedCount);
        state.addProperty("totalCount", entries.size());
        state.add("items", entries);
        return state;
    }

    private static final class DiaryTier
    {
        private final String area;
        private final String tier;
        private final int varbit;

        private DiaryTier(String area, String tier, int varbit)
        {
            this.area = area;
            this.tier = tier;
            this.varbit = varbit;
        }
    }
}
