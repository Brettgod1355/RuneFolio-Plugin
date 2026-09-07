package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioDiaryTaskParserTest
{
    @Test
    public void sendsOnlyCompletedTaskNamesFromEveryTier()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Karamja Area Tasks");
        JsonObject state = RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Karamja Area Tasks",
            "Easy Tasks",
            "<str>Pick five bananas from the plantation located east of the volcano.</str>",
            "Use the rope swing to travel to the small island north-west of Karamja.",
            "Requirements: 99 imaginary levels",
            "Medium Tasks",
            "<str>Complete a medium task without truncating its name.</str>",
            "Hard Tasks",
            "Complete a hard task.",
            "Elite Tasks",
            "<str>Complete an elite task.</str>"
        ));

        Assert.assertNotNull(state);
        Assert.assertEquals("karamja", state.get("area").getAsString());
        JsonArray tiers = state.getAsJsonArray("tiers");
        Assert.assertEquals(4, tiers.size());

        JsonArray easyTasks = tiers.get(0).getAsJsonObject().getAsJsonArray("completedTaskNames");
        Assert.assertEquals(1, easyTasks.size());
        Assert.assertEquals(
            "Pick five bananas from the plantation located east of the volcano.",
            easyTasks.get(0).getAsString()
        );
        Assert.assertEquals(0, tiers.get(2).getAsJsonObject().getAsJsonArray("completedTaskNames").size());
    }

    @Test
    public void stripsNumbersFromCompletedTaskRows()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Ardougne Area Tasks");
        JsonObject state = RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Easy", "<str>1. An easy task</str>", "Medium", "<str>1. A medium task</str>",
            "Hard", "<str>1. A hard task</str>", "Elite", "<str>1. An elite task</str>"
        ));

        Assert.assertNotNull(state);
        Assert.assertEquals(
            "An easy task",
            state.getAsJsonArray("tiers").get(0).getAsJsonObject()
                .getAsJsonArray("completedTaskNames").get(0).getAsString()
        );
    }

    @Test
    public void recognizesAllDiaryAreaTitles()
    {
        String[] titles = {
            "Ardougne Area Tasks", "Desert Tasks", "Falador Area Tasks", "Fremennik Tasks",
            "Kandarin Tasks", "Karamja Area Tasks", "Kourend & Kebos Tasks",
            "Lumbridge & Draynor Tasks", "Morytania Tasks", "Varrock Tasks",
            "Western Area Tasks", "Wilderness Area Tasks"
        };
        String[] keys = {
            "ardougne", "desert", "falador", "fremennik", "kandarin", "karamja",
            "kourend_kebos", "lumbridge_draynor", "morytania", "varrock",
            "western_provinces", "wilderness"
        };

        for (int index = 0; index < titles.length; index++)
        {
            RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle(titles[index]);
            Assert.assertNotNull(titles[index], area);
            Assert.assertEquals(keys[index], area.getKey());
        }
    }

    @Test
    public void preservesWrappedWordsAndQuestRequirements()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Achievement Diary - Ardougne");
        JsonObject state = RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Easy", "Medium", "Hard",
            "<str>Have a zookeeper put you in Ardougne Zoo's monkey<br>cage."
                + "(<col=0000ff><str>Started Monkey Madness I</str></col>)</str>",
            "Elite"
        ));
        Assert.assertNotNull(state);
        Assert.assertEquals("Have a zookeeper put you in Ardougne Zoo's monkey cage.(Started Monkey Madness I)",
            state.getAsJsonArray("tiers").get(2).getAsJsonObject()
                .getAsJsonArray("completedTaskNames").get(0).getAsString());
    }

    @Test
    public void handlesFormattingInEveryTierWithoutTreatingRequirementsAsTaskCompletion()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Ardougne Tasks");
        for (String heading : Arrays.asList("Easy", "Medium", "Hard", "Elite"))
        {
            for (String breakTag : Arrays.asList("<br>", "<br/>", "<BR />"))
            {
                java.util.List<String> lines = new java.util.ArrayList<>();
                for (String tier : Arrays.asList("Easy", "Medium", "Hard", "Elite"))
                {
                    lines.add(tier);
                    if (heading.equals(tier))
                    {
                        lines.add("<col=00ff00><str=ff0000>1. A completed" + breakTag + "task.</str></col>");
                        lines.add("An unfinished task.(<col=0000ff><str>Started Example Quest</str></col>)");
                        lines.add("Another unfinished task.(<str>50 Magic</str>)");
                    }
                }
                JsonObject state = RuneFolioDiaryTaskParser.parse(area, lines);
                Assert.assertNotNull(state);
                for (int index = 0; index < 4; index++)
                {
                    JsonObject tier = state.getAsJsonArray("tiers").get(index).getAsJsonObject();
                    JsonArray completed = tier.getAsJsonArray("completedTaskNames");
                    if (heading.equalsIgnoreCase(tier.get("tier").getAsString()))
                    {
                        Assert.assertEquals(1, completed.size());
                        Assert.assertEquals("A completed task.", completed.get(0).getAsString());
                    }
                    else
                    {
                        Assert.assertEquals(0, completed.size());
                    }
                }
            }
        }
    }

    @Test
    public void rejectsAnIncompleteInterfaceCapture()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Varrock Tasks");
        Assert.assertNull(RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Easy Tasks", "<str>1. An easy task</str>", "Medium Tasks", "<str>1. A medium task</str>"
        )));
    }
}
