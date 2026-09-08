package app.runefolio.sync;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded result-message correlation. Call only with local system messages. */
final class RuneFolioBossRecordTracker
{
    private static final Pattern ALT_COUNT = Pattern.compile("^Your completion count for (.+) is: ?([0-9,]+)\\.?$");
    private static final Pattern COUNT = Pattern.compile("^Your (.+) (?:kill|chest|completion) count is: ?([0-9,]+)\\.?$");
    private static final Pattern TIME = Pattern.compile("(?:Fight duration|Challenge time|Challenge duration|Corrupted challenge duration|Duration|Completion time): ([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\.[0-9]{1,3})?)");
    static final Set<String> SOURCES = Set.of("Abyssal Sire","Alchemical Hydra","Amoxliatl","Araxxor","Artio","Barrows Chests","Brutus","Bryophyta","Callisto","Calvar'ion","Cerberus","Chambers of Xeric","Chambers of Xeric: Challenge Mode","Chaos Elemental","Chaos Fanatic","Commander Zilyana","Corporeal Beast","Crazy Archaeologist","Dagannoth Prime","Dagannoth Rex","Dagannoth Supreme","Deranged Archaeologist","Doom of Mokhaiotl","Duke Sucellus","General Graardor","Giant Mole","Grotesque Guardians","Hespori","Kalphite Queen","King Black Dragon","Kraken","Kree'Arra","K'ril Tsutsaroth","Lunar Chests","Mad Angel","Maggot King","Mimic","Nex","Nightmare","Phosani's Nightmare","Obor","Phantom Muspah","Sarachnis","Scorpia","Scurrius","Shellbane Gryphon","Skotizo","Sol Heredit","Spindel","Tempoross","The Gauntlet","The Corrupted Gauntlet","The Hueycoatl","The Leviathan","The Royal Titans","The Whisperer","Theatre of Blood","Theatre of Blood: Hard Mode","Thermonuclear Smoke Devil","Tombs of Amascut","Tombs of Amascut: Expert Mode","TzKal-Zuk","TzTok-Jad","Vardorvis","Venenatis","Vet'ion","Vorkath","Wintertodt","Yama","Zalcano","Zulrah","Barrows","Gauntlet","Corrupted Gauntlet","Tombs of Amascut: Entry Mode","Theatre of Blood: Entry Mode");
    private String lastKey;
    private JsonObject pending;
    private int countTick = -100;
    private int timeTick = -100;
    private Long duration;
    private Integer partySize;
    private int partyTick = -100;
    private boolean personalBest;
    private boolean challengeTime;

    void message(String message, int tick, Set<String> knownSources)
    {
        if (message == null || message.length() > 500) return;
        Matcher count = COUNT.matcher(message);
        boolean counted = count.matches();
        if (!counted) { count = ALT_COUNT.matcher(message); counted = count.matches(); }
        String source = counted ? canonicalSource(count.group(1), knownSources) : null;
        if (source != null)
        {
            try
            {
                int value = Integer.parseInt(count.group(2).replace(",", ""));
                if (value < 1 || (source + ":" + value).equals(lastKey)) return;
                // Never reuse timing left over from an earlier result.
                if (Math.abs(tick - timeTick) > 2) { duration = null; personalBest = false; challengeTime = false; }
                pending = new JsonObject();
                pending.addProperty("sourceName", source);
                pending.addProperty("count", value);
                countTick = tick;
            }
            catch (NumberFormatException ignored) { }
        }
        Matcher team = Pattern.compile("Team size: (Solo|[0-9]{1,2})(?: players)?").matcher(message);
        if (team.find())
        {
            int size = "Solo".equals(team.group(1)) ? 1 : Integer.parseInt(team.group(1));
            if (size > 0) { partySize = size; partyTick = tick; }
        }
        Matcher time = TIME.matcher(message);
        if (time.find())
        {
            Long parsed = millis(time.group(1));
            boolean challenge = message.startsWith("Challenge time:") || message.startsWith("Challenge duration:");
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
        if (partySize != null && Math.abs(partyTick - countTick) <= 2
            && record.get("sourceName").getAsString().startsWith("Chambers of Xeric")) record.addProperty("partySize", partySize);
        lastKey = record.get("sourceName").getAsString() + ":" + record.get("count").getAsInt();
        clearPending();
        return record;
    }

    private static String canonicalSource(String name, Set<String> sources)
    {
        for (String source : sources) if (source.equalsIgnoreCase(name.trim())) return source;
        return null;
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

    void reset() { lastKey = null; clearPending(); }
    private void clearPending() { partySize = null; partyTick = -100; pending = null; countTick = -100; timeTick = -100; duration = null; personalBest = false; challengeTime = false; }
}
