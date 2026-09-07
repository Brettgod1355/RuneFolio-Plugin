package app.runefolio.sync;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded result-message correlation. Call only with local system messages. */
final class RuneFolioBossRecordTracker
{
    private static final Pattern COUNT = Pattern.compile("^Your (.+) (?:kill|chest|completion) count is: ?([0-9,]+)\\.?$");
    private static final Pattern TIME = Pattern.compile("(?:Fight duration|Challenge time|Duration|Completion time): ([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\.[0-9]{1,3})?)");
    private JsonObject pending;
    private int countTick = -100;
    private int timeTick = -100;
    private Long duration;
    private boolean personalBest;
    private boolean challengeTime;

    void message(String message, int tick, Set<String> knownSources)
    {
        if (message == null || message.length() > 500) return;
        Matcher count = COUNT.matcher(message);
        if (count.matches() && knownSources.contains(count.group(1)))
        {
            try
            {
                int value = Integer.parseInt(count.group(2).replace(",", ""));
                if (value < 1) return;
                // Never reuse timing left over from an earlier result.
                if (Math.abs(tick - timeTick) > 2) { duration = null; personalBest = false; challengeTime = false; }
                pending = new JsonObject();
                pending.addProperty("sourceName", count.group(1));
                pending.addProperty("count", value);
                countTick = tick;
            }
            catch (NumberFormatException ignored) { }
        }
        Matcher time = TIME.matcher(message);
        if (time.find())
        {
            Long parsed = millis(time.group(1));
            boolean challenge = message.startsWith("Challenge time:");
            if (parsed != null && (!challengeTime || challenge || tick - timeTick > 2))
            {
                duration = parsed;
                timeTick = tick;
                personalBest = message.toLowerCase(java.util.Locale.ROOT).contains("new personal best");
                challengeTime = challenge;
            }
        }
    }

    JsonObject poll(int tick)
    {
        if (pending == null || tick - countTick < 2) return null;
        JsonObject record = pending.deepCopy();
        if (duration != null && Math.abs(timeTick - countTick) <= 2)
        {
            record.addProperty("durationMillis", duration);
            record.addProperty("newPersonalBest", personalBest);
            record.addProperty("timingKind", challengeTime ? "challenge" : "completion");
        }
        reset();
        return record;
    }

    static Long millis(String text)
    {
        try
        {
            String[] parts = text.split(":");
            if (parts.length < 2 || parts.length > 3) return null;
            double seconds = Double.parseDouble(parts[parts.length - 1]);
            int minutes = Integer.parseInt(parts[parts.length - 2]);
            int hours = parts.length == 3 ? Integer.parseInt(parts[0]) : 0;
            if (seconds < 0 || seconds >= 60 || minutes < 0 || minutes >= 60 || hours < 0 || hours > 23) return null;
            return Math.round((hours * 3600 + minutes * 60 + seconds) * 1000);
        }
        catch (NumberFormatException ignored) { return null; }
    }

    void reset() { pending = null; countTick = -100; timeTick = -100; duration = null; personalBest = false; challengeTime = false; }
}
