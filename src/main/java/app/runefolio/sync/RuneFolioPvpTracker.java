package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Bounded post-fight correlation only. No player equipment, locations or combat assistance. */
final class RuneFolioPvpTracker
{
    static final class Result
    {
        final UUID id = UUID.randomUUID();
        final String occurredAt;
        final int tick;
        final JsonObject payload = new JsonObject();
        boolean historyEnabled;
        String characterName, identityKey;
        Result(String opponent, int tick, String occurredAt)
        {
            this.tick = tick; this.occurredAt = occurredAt;
            payload.addProperty("kind", "kill");
            payload.addProperty("evidence", "finishing_hit");
            payload.addProperty("opponent", opponent);
        }
    }
    private static final class Loot
    {
        final String name; final int tick; final JsonArray items;
        Loot(String name, int tick, JsonArray items) { this.name = name; this.tick = tick; this.items = items.deepCopy(); }
    }
    private final List<Result> pending = new ArrayList<>();
    private final List<Loot> earlyLoot = new ArrayList<>();

    Result death(String name, int tick, String occurredAt)
    {
        if (name == null || !name.matches("[a-zA-Z0-9 _-]{1,12}") || name.isBlank() || pending.size() >= 32) return null;
        String key = normalise(name);
        if (pending.stream().anyMatch(r -> normalise(r.payload.get("opponent").getAsString()).equals(key))) return null;
        Result result = new Result(name.trim(), tick, occurredAt);
        List<Loot> matching = new ArrayList<>();
        for (Loot loot : earlyLoot) if (normalise(loot.name).equals(key) && tick - loot.tick >= 0 && tick - loot.tick <= 1) matching.add(loot);
        if (matching.size() == 1) result.payload.add("items", matching.get(0).items.deepCopy());
        earlyLoot.removeIf(loot -> normalise(loot.name).equals(key));
        pending.add(result);
        return result;
    }

    void loot(String name, int tick, JsonArray items)
    {
        if (name == null || items == null || items.size() > 100) return;
        List<Result> matching = new ArrayList<>();
        for (Result result : pending) if (normalise(name).equals(normalise(result.payload.get("opponent").getAsString()))
            && tick >= result.tick && tick - result.tick <= 8) matching.add(result);
        if (matching.size() == 1) {
            Result result = matching.get(0);
            if (!result.payload.has("items")) result.payload.add("items", items.deepCopy());
        } else if (matching.isEmpty() && earlyLoot.size() < 32) earlyLoot.add(new Loot(name, tick, items));
    }

    List<Result> poll(int tick)
    {
        List<Result> ready = new ArrayList<>();
        pending.removeIf(result -> { if (tick - result.tick >= 9) { ready.add(result); return true; } return false; });
        earlyLoot.removeIf(loot -> tick - loot.tick > 1 || tick < loot.tick);
        return ready;
    }
    void reset() { pending.clear(); earlyLoot.clear(); }
    List<Result> finish() { List<Result> result = new ArrayList<>(pending); reset(); return result; }
    private static String normalise(String name) { return name.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT); }
}
