package app.runefolio.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Quest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Data-only configuration: cannot add scripts, URLs, varps or executable behavior. */
final class RuneFolioCollectorManifest
{
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static volatile RuneFolioCollectorManifest current;
    private final int combatTaskCount;
    private final Set<Quest> exclusions;
    private RuneFolioCollectorManifest(int count, Set<Quest> exclusions)
    {
        this.combatTaskCount = count;
        this.exclusions = Set.copyOf(exclusions);
    }

    static RuneFolioCollectorManifest parse(JsonObject json)
    {
        if (json == null || !json.keySet().equals(Set.of("schemaVersion", "revision", "combatTaskCount", "questCapeExclusions"))) return null;
        try
        {
            if (!integer(json.get("schemaVersion")) || json.get("schemaVersion").getAsInt() != 1
                || !integer(json.get("combatTaskCount"))) return null;
            int count = json.get("combatTaskCount").getAsInt();
            // Cannot expand beyond the compiled, reviewed CA bitmap allowlist.
            if (count < 1 || count > RuneFolioCombatTaskCatalog.MAX_TASK_COUNT || !json.get("revision").isJsonPrimitive()
                || !json.get("revision").getAsJsonPrimitive().isString()
                || !json.get("revision").getAsString().matches("[A-Za-z0-9._-]{1,40}")
                || !json.get("questCapeExclusions").isJsonArray()
                || json.getAsJsonArray("questCapeExclusions").size() > 100) return null;
            Set<Quest> exclusions = new HashSet<>();
            for (JsonElement element : json.getAsJsonArray("questCapeExclusions"))
            {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                    || !exclusions.add(Quest.valueOf(element.getAsString()))) return null;
            }
            return new RuneFolioCollectorManifest(count, exclusions);
        }
        catch (RuntimeException invalid) { return null; }
    }

    private static boolean integer(JsonElement value)
    {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
            && value.getAsString().matches("[0-9]{1,4}");
    }

    static RuneFolioCollectorManifest fetch(OkHttpClient client) throws IOException
    {
        OkHttpClient scoped = RuneFolioApiClient.scopedClient(client, 5, 5);
        Request request = new Request.Builder()
            .url("https://runefolio.app/api/collector-manifest")
            .header("Accept", "application/json")
            .build();
        try (Response response = scoped.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return null;
            byte[] bytes = RuneFolioApiClient.readBounded(response.body(), MAX_MANIFEST_BYTES);
            if (bytes == null) return null;
            JsonElement json = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
            return json.isJsonObject() ? parse(json.getAsJsonObject()) : null;
        }
        catch (RuntimeException invalid) { return null; }
    }

    static void install(RuneFolioCollectorManifest manifest) { if (manifest != null) current = manifest; }
    static void reset() { current = null; }
    static int combatTaskCount(int fallback) { return current == null ? fallback : current.combatTaskCount; }
    static boolean isSupplemental(Quest quest, boolean fallback) { return current == null ? fallback : current.exclusions.contains(quest); }
    static int supplementalCount(int fallback) { return current == null ? fallback : current.exclusions.size(); }
}
