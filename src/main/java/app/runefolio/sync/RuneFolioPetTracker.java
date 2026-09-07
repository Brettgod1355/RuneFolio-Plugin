package app.runefolio.sync;

import java.util.Set;

/** Correlates only local pet messages with known pet item names; no clan/player chat. */
final class RuneFolioPetTracker
{
    private int started = -1;
    private int candidateTick = -100;
    private String candidate;
    private boolean duplicate;
    private boolean backpack;

    void message(String message, int tick, Set<String> petNames)
    {
        if (message.startsWith("You have a funny feeling like you're being followed")
            || message.startsWith("You feel something weird sneaking into your backpack")
            || message.startsWith("You have a funny feeling like you would have been followed"))
        {
            if (started >= 0) return;
            started = tick;
            duplicate = message.contains("would have been");
            backpack = message.contains("backpack");
            if (tick - candidateTick > 5) candidate = null;
        }
        String prefix = message.startsWith("Untradeable drop: ") ? "Untradeable drop: "
            : message.startsWith("New item added to your collection log: ")
                ? "New item added to your collection log: " : null;
        if (prefix != null) observeName(message.substring(prefix.length()).trim(), tick, petNames);
    }

    void observeName(String name, int tick, Set<String> petNames)
    {
        if (petNames.contains(name)) { candidate = name; candidateTick = tick; }
    }

    String pollCaption(int tick)
    {
        if (started < 0 || tick - started < 5) return null;
        String name = candidate != null && Math.abs(candidateTick - started) <= 5 ? candidate : "Unidentified pet";
        String caption = (duplicate ? "Duplicate pet: " : "Pet received: ") + name
            + (backpack ? " (backpack)" : "");
        reset();
        return caption;
    }

    void reset() { started = -1; candidateTick = -100; candidate = null; duplicate = false; backpack = false; }
}
