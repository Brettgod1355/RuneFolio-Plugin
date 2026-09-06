package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioDiaryTaskParserTest
{
    @Test
    public void parsesEveryTierAndPreservesFullTaskNames()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Karamja Area Tasks");
        JsonObject state = RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Karamja Area Tasks",
            "Easy Tasks",
            "<str>Pick five bananas from the plantation located east of the volcano.</str>",
            "Use the rope swing to travel to the small island north-west of Karamja.",
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
        JsonArray easyTasks = tiers.get(0).getAsJsonObject().getAsJsonArray("tasks");
        Assert.assertEquals(2, easyTasks.size());
        Assert.assertTrue(easyTasks.get(0).getAsJsonObject().get("completed").getAsBoolean());
        Assert.assertFalse(easyTasks.get(1).getAsJsonObject().get("completed").getAsBoolean());
        Assert.assertEquals(
            "Use the rope swing to travel to the small island north-west of Karamja.",
            easyTasks.get(1).getAsJsonObject().get("name").getAsString()
        );
    }

    @Test
    public void stillAcceptsNumberedTaskRows()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Ardougne Area Tasks");
        JsonObject state = RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Easy", "1. An easy task", "Medium", "1. A medium task",
            "Hard", "1. A hard task", "Elite", "1. An elite task"
        ));

        Assert.assertNotNull(state);
        Assert.assertEquals(
            "An easy task",
            state.getAsJsonArray("tiers").get(0).getAsJsonObject()
                .getAsJsonArray("tasks").get(0).getAsJsonObject().get("name").getAsString()
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
    public void rejectsAnIncompleteInterfaceCapture()
    {
        RuneFolioDiaryTaskParser.Area area = RuneFolioDiaryTaskParser.areaFromTitle("Varrock Tasks");
        Assert.assertNull(RuneFolioDiaryTaskParser.parse(area, Arrays.asList(
            "Easy Tasks", "1. An easy task", "Medium Tasks", "1. A medium task"
        )));
    }
}
