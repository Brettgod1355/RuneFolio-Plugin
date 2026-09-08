package app.runefolio.sync;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RuneFolioClueRecordTracker
{
    private static final Pattern COMPLETE = Pattern.compile("^You have completed ([0-9,]+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.$", Pattern.CASE_INSENSITIVE);
    private JsonObject pending;
    private int started = -100;
    private String lastKey;

    void message(String message, int tick)
    {
        if (message == null || message.length() > 180) return;
        Matcher match = COMPLETE.matcher(message);
        if (!match.matches()) return;
        try
        {
            int count = Integer.parseInt(match.group(1).replace(",", ""));
            if (count < 1) return;
            String tier = match.group(2).toLowerCase(java.util.Locale.ROOT);
            String key = tier + ":" + count;
            if (key.equals(lastKey)) return;
            pending = new JsonObject();
            pending.addProperty("tier", tier);
            pending.addProperty("count", count);
            started = tick;
        }
        catch (NumberFormatException ignored) { }
    }

    JsonObject rewards(JsonArray rewards, int tick) { return rewards(rewards, tick, null); }

    JsonObject rewards(JsonArray rewards, int tick, String tier)
    {
        if (pending != null && tier != null && !tier.equals(pending.get("tier").getAsString())) return null;
        if (pending == null || tick - started > 5 || rewards == null || rewards.size() > 100)
        {
            pending = null;
            return null;
        }
        JsonObject record = pending.deepCopy();
        record.add("items", rewards.deepCopy());
        lastKey = record.get("tier").getAsString() + ":" + record.get("count").getAsInt();
        pending = null;
        return record;
    }

    JsonObject poll(int tick)
    {
        if (pending == null || tick - started < 6) return null;
        JsonObject record = pending.deepCopy();
        lastKey = record.get("tier").getAsString() + ":" + record.get("count").getAsInt();
        pending = null;
        return record;
    }

    void reset() { pending = null; started = -100; lastKey = null; }
}
