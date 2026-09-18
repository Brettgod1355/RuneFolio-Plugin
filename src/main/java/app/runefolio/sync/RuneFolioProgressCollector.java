/*
 * Diary count/POH guard references: RuneProfile; see THIRD_PARTY_NOTICES.md.
 * BSD 2-Clause License
 * 
 * Copyright (c) 2022, Reinhardt Rijna
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

final class RuneFolioProgressCollector
{
    private static final int DIARY_COMPLETION_INFO_SCRIPT = 2200;
    private static final String[] DIARY_TIER_NAMES = {"easy", "medium", "hard", "elite"};
    // Tiers with this varbit are derived solely from the task-count script in collectDiaryTierTaskCounts.
    private static final int DERIVED_FROM_TASK_COUNTS = -1;

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
        new DiaryTier("karamja", "easy", DERIVED_FROM_TASK_COUNTS),
        new DiaryTier("karamja", "medium", DERIVED_FROM_TASK_COUNTS),
        new DiaryTier("karamja", "hard", DERIVED_FROM_TASK_COUNTS),
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

    private static final DiaryArea[] DIARY_AREAS = new DiaryArea[]
    {
        new DiaryArea("ardougne", 1),
        new DiaryArea("desert", 5),
        new DiaryArea("falador", 2),
        new DiaryArea("fremennik", 3),
        new DiaryArea("kandarin", 4),
        new DiaryArea("karamja", 0),
        new DiaryArea("kourend_kebos", 11),
        new DiaryArea("lumbridge_draynor", 6),
        new DiaryArea("morytania", 7),
        new DiaryArea("varrock", 8),
        new DiaryArea("western_provinces", 10),
        new DiaryArea("wilderness", 9)
    };

    private RuneFolioProgressCollector()
    {
    }

    static int supplementalQuestCount()
    {
        return RuneFolioCollectorManifest.supplementalCount(SUPPLEMENTAL_QUESTS.size());
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
            boolean countsTowardQuestCape = !RuneFolioCollectorManifest.isSupplemental(quest, SUPPLEMENTAL_QUESTS.contains(quest));
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
        state.addProperty("questPoints", client.getVarpValue(VarPlayerID.QP));
        state.addProperty("mainQuestCompleted", mainQuestCompleted);
        state.addProperty("mainQuestTotal", mainQuestTotal);
        state.addProperty("supplementalQuestEntries", supplementalQuestCount());
        return state;
    }

    static JsonObject diaries(Client client)
    {
        Set<String> completedTierKeys = new LinkedHashSet<>();
        for (DiaryTier diaryTier : DIARY_TIERS)
        {
            if (diaryTier.varbit == DERIVED_FROM_TASK_COUNTS)
            {
                continue;
            }
            if (client.getVarbitValue(diaryTier.varbit) > 0)
            {
                completedTierKeys.add(diaryTier.area + "." + diaryTier.tier);
            }
        }

        JsonArray tierTaskCounts = collectDiaryTierTaskCounts(client, completedTierKeys);
        JsonArray completedTiers = new JsonArray();
        for (String completedTierKey : completedTierKeys)
        {
            completedTiers.add(completedTierKey);
        }

        JsonObject state = new JsonObject();
        state.add("completedTiers", completedTiers);
        state.add("tierTaskCounts", tierTaskCounts);
        state.addProperty("trackedTierCount", DIARY_TIERS.length);
        state.addProperty("coverage", "all 48 tier task counts and individual completion flags; identities withheld if counters disagree");
        state.add("taskAreas", RuneFolioDiaryTaskFlags.collect(client, tierTaskCounts));
        return state;
    }

    private static JsonArray collectDiaryTierTaskCounts(Client client, Set<String> completedTierKeys)
    {
        JsonArray areas = new JsonArray();
        for (DiaryArea diaryArea : DIARY_AREAS)
        {
            client.runScript(DIARY_COMPLETION_INFO_SCRIPT, diaryArea.id);
            if (client.getIntStackSize() < 12)
            {
                continue;
            }

            JsonObject area = diaryTierTaskCounts(diaryArea.area, client.getIntStack());
            if (area == null)
            {
                continue;
            }
            for (JsonElement element : area.getAsJsonArray("tiers"))
            {
                JsonObject tier = element.getAsJsonObject();
                if (tier.get("totalCount").getAsInt() > 0
                    && tier.get("completedCount").getAsInt() >= tier.get("totalCount").getAsInt())
                {
                    completedTierKeys.add(diaryArea.area + "." + tier.get("tier").getAsString());
                }
            }
            areas.add(area);
        }
        return areas;
    }

    static JsonObject diaryTierTaskCounts(String areaName, int[] stack)
    {
        if (areaName == null || areaName.isBlank() || stack == null || stack.length < 12)
        {
            return null;
        }

        JsonArray tiers = new JsonArray();
        for (int tierIndex = 0; tierIndex < DIARY_TIER_NAMES.length; tierIndex++)
        {
            int stackIndex = tierIndex * 3;
            JsonObject tier = new JsonObject();
            tier.addProperty("tier", DIARY_TIER_NAMES[tierIndex]);
            tier.addProperty("completedCount", Math.max(0, stack[stackIndex]));
            tier.addProperty("totalCount", Math.max(0, stack[stackIndex + 1]));
            tiers.add(tier);
        }

        JsonObject area = new JsonObject();
        area.addProperty("area", areaName);
        area.add("tiers", tiers);
        return area;
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
        // A POH Adventure Log displays the host's items, not the local player's.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0)
        {
            return null;
        }

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

    private static final class DiaryArea
    {
        private final String area;
        private final int id;

        private DiaryArea(String area, int id)
        {
            this.area = area;
            this.id = id;
        }
    }
}

