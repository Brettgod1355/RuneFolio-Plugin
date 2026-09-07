package app.runefolio.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Quest;

/** Data-only configuration: cannot add scripts, URLs, varps or executable behavior. */
final class RuneFolioCollectorManifest
{
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
            if (count < 1 || count > 672 || !json.get("revision").isJsonPrimitive()
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

    static RuneFolioCollectorManifest fetch() throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://runefolio.app/api/collector-manifest").openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");
        try
        {
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > 65536) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream())
            {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1)
                {
                    if (bytes.size() + read > 65536) return null;
                    bytes.write(buffer, 0, read);
                }
                JsonElement json = new JsonParser().parse(bytes.toString(StandardCharsets.UTF_8.name()));
                return json.isJsonObject() ? parse(json.getAsJsonObject()) : null;
            }
        }
        catch (RuntimeException invalid) { return null; }
        finally { connection.disconnect(); }
    }

    static void install(RuneFolioCollectorManifest manifest) { if (manifest != null) current = manifest; }
    static int combatTaskCount(int fallback) { return current == null ? fallback : current.combatTaskCount; }
    static boolean isSupplemental(Quest quest, boolean fallback) { return current == null ? fallback : current.exclusions.contains(quest); }
    static int supplementalCount(int fallback) { return current == null ? fallback : current.exclusions.size(); }
}
