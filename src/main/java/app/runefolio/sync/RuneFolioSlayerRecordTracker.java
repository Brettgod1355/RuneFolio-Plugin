package app.runefolio.sync;
import com.google.gson.JsonObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RuneFolioSlayerRecordTracker
{
    private static final Pattern TASK = Pattern.compile("^You have completed your task! You killed ([0-9,]+) ([^.]{1,120})\\..*$");
    private static final Pattern POINTS = Pattern.compile("^You've completed (?:at least )?([0-9,]+) (Wilderness |Mortimer )?tasks?(?: and received ([0-9,]+) points, giving you a total of ([0-9,]+))?.*$");
    private String lastMessage;
    private int lastTick = -100;
    private JsonObject task;
    private JsonObject points;
    private int taskTick = -100, pointsTick = -100;

    void message(String message, int tick)
    {
        if (message == null || message.length() > 500) return;
        if (message.equals(lastMessage) && tick - lastTick <= 5) return;
        lastMessage = message; lastTick = tick;
        try
        {
            Matcher match = TASK.matcher(message);
            if (match.matches())
            {
                task = new JsonObject();
                task.addProperty("monster", match.group(2).trim());
                task.addProperty("kills", numeric(match.group(1)));
                taskTick = tick;
            }
            match = POINTS.matcher(message);
            if (match.matches())
            {
                points = new JsonObject();
                points.addProperty("tasksCompleted", numeric(match.group(1)));
                points.addProperty("streakType", match.group(2) == null ? "standard" : match.group(2).trim().toLowerCase(java.util.Locale.ROOT));
                if (match.group(3) != null) points.addProperty("pointsEarned", numeric(match.group(3)));
                if (match.group(4) != null) points.addProperty("totalPoints", numeric(match.group(4)));
                pointsTick = tick;
            }
        }
        catch (NumberFormatException ignored) { reset(); }
    }

    JsonObject poll(int tick)
    {
        if (task == null && points == null) return null;
        int anchor = task != null ? taskTick : pointsTick;
        if (tick - anchor < 3) return null;
        JsonObject record = task == null ? new JsonObject() : task.deepCopy();
        if (points != null && (task == null || Math.abs(pointsTick - taskTick) <= 3))
            for (String key : points.keySet()) record.add(key, points.get(key));
        task = null; points = null; taskTick = -100; pointsTick = -100;
        return record;
    }

    private int numeric(String value) { return Integer.parseInt(value.replace(",", "")); }
    void reset() { lastMessage = null; lastTick = -100; task = null; points = null; taskTick = -100; pointsTick = -100; }
}
