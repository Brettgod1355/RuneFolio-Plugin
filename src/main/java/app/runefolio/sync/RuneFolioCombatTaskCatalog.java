package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.Client;

final class RuneFolioCombatTaskCatalog
{
    // The game stores Combat Achievement completion as consecutive 32-bit varp
    // bitmaps. Task index N is bit N % 32 of varp N / 32.
    private static final int[] COMPLETION_VARPS = {
        3116, 3117, 3118, 3119, 3120, 3121, 3122, 3123, 3124, 3125, 3126,
        3127, 3128, 3387, 3718, 3773, 3774, 4204, 4496, 4721, 5673
    };
    static final int MAX_TASK_COUNT = COMPLETION_VARPS.length * Integer.SIZE;
    private static final int TASK_COUNT = 655;

    private RuneFolioCombatTaskCatalog()
    {
    }

    static JsonArray completedTaskKeys(Client client)
    {
        JsonArray completed = new JsonArray();
        for (int taskIndex = 0; taskIndex < trackedTaskCount(); taskIndex++)
        {
            if (isCompleted(client, taskIndex))
            {
                completed.add(taskKey(taskIndex));
            }
        }
        return completed;
    }

    static JsonArray taskStates(Client client)
    {
        JsonArray states = new JsonArray();
        for (int taskIndex = 0; taskIndex < trackedTaskCount(); taskIndex++)
        {
            JsonObject state = new JsonObject();
            state.addProperty("key", taskKey(taskIndex));
            state.addProperty("completed", isCompleted(client, taskIndex));
            states.add(state);
        }
        return states;
    }

    static int trackedTaskCount()
    {
        return RuneFolioCollectorManifest.combatTaskCount(TASK_COUNT);
    }

    private static boolean isCompleted(Client client, int taskIndex)
    {
        int varpIndex = taskIndex / Integer.SIZE;
        int bitIndex = taskIndex % Integer.SIZE;
        return ((client.getVarpValue(COMPLETION_VARPS[varpIndex]) >>> bitIndex) & 1) == 1;
    }

    private static String taskKey(int taskIndex)
    {
        return "ca_task_index_" + taskIndex;
    }
}
