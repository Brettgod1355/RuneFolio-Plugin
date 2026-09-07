package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class RuneFolioDiaryTaskParser
{
    private static final List<String> TIER_ORDER = Arrays.asList("easy", "medium", "hard", "elite");
    private static final Pattern NUMBERED_TASK_PATTERN = Pattern.compile("^(\\d+)\\s*[.):~-]\\s*(.+)$");
    private static final Pattern COMPLETED_ROW_PREFIX = Pattern.compile(
        "^(?:\\s|</?col(?:=[0-9a-f]+)?>)*<str(?:=[0-9a-f]+)?>", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Area> AREAS_BY_TITLE = new LinkedHashMap<>();

    static
    {
        register("ARDOUGNE", "ardougne", "Ardougne");
        register("DESERT", "desert", "Desert");
        register("FALADOR", "falador", "Falador");
        register("FREMENNIK", "fremennik", "Fremennik");
        register("KANDARIN", "kandarin", "Kandarin");
        register("KARAMJA", "karamja", "Karamja");
        register("KOUREND & KEBOS", "kourend_kebos", "Kourend & Kebos");
        register("LUMBRIDGE & DRAYNOR", "lumbridge_draynor", "Lumbridge & Draynor");
        register("MORYTANIA", "morytania", "Morytania");
        register("VARROCK", "varrock", "Varrock");
        register("WESTERN", "western_provinces", "Western Provinces");
        register("WILDERNESS", "wilderness", "Wilderness");
    }

    private RuneFolioDiaryTaskParser()
    {
    }

    static Area areaFromTitle(String title)
    {
        String plainTitle = clean(title).toUpperCase(Locale.ROOT);
        for (Map.Entry<String, Area> entry : AREAS_BY_TITLE.entrySet())
        {
            if (plainTitle.contains(entry.getKey()))
            {
                return entry.getValue();
            }
        }
        return null;
    }

    static JsonObject parse(Area area, Widget[] children)
    {
        if (area == null || children == null)
        {
            return null;
        }

        List<String> lines = new ArrayList<>();
        for (Widget child : children)
        {
            if (child != null && child.getText() != null)
            {
                lines.add(child.getText());
            }
        }
        return parse(area, lines);
    }

    static JsonObject parse(Area area, List<String> rawLines)
    {
        if (area == null || rawLines == null)
        {
            return null;
        }

        Map<String, JsonArray> completedTasksByTier = new LinkedHashMap<>();
        for (String tier : TIER_ORDER)
        {
            completedTasksByTier.put(tier, new JsonArray());
        }

        Set<String> seenTiers = new HashSet<>();
        String currentTier = null;
        for (String rawLine : rawLines)
        {
            if (rawLine == null)
            {
                continue;
            }
            String plainLine = clean(rawLine);
            String tier = tierFromHeading(plainLine);
            if (tier != null)
            {
                currentTier = tier;
                seenTiers.add(tier);
                continue;
            }
            if (currentTier == null || !COMPLETED_ROW_PREFIX.matcher(rawLine).find())
            {
                continue;
            }

            Matcher matcher = NUMBERED_TASK_PATTERN.matcher(plainLine);
            String taskName = matcher.matches() ? matcher.group(2).trim() : plainLine;
            if (!taskName.isEmpty())
            {
                completedTasksByTier.get(currentTier).add(taskName);
            }
        }

        if (seenTiers.size() != TIER_ORDER.size())
        {
            return null;
        }

        JsonArray tiers = new JsonArray();
        for (String tier : TIER_ORDER)
        {
            JsonObject tierState = new JsonObject();
            tierState.addProperty("tier", tier);
            tierState.add("completedTaskNames", completedTasksByTier.get(tier));
            tiers.add(tierState);
        }

        JsonObject areaState = new JsonObject();
        areaState.addProperty("area", area.key);
        areaState.addProperty("name", area.name);
        areaState.add("tiers", tiers);
        return areaState;
    }

    private static String tierFromHeading(String value)
    {
        String normalized = value.toLowerCase(Locale.ROOT).replace(':', ' ').trim();
        for (String tier : TIER_ORDER)
        {
            if (normalized.equals(tier)
                || normalized.equals(tier + " tasks")
                || normalized.equals(tier + " achievements"))
            {
                return tier;
            }
        }
        return null;
    }

    private static String clean(String value)
    {
        return Text.removeTags((value == null ? "" : value).replaceAll("(?i)<br\\s*/?>", " "))
            .replace('\u00a0', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static void register(String titleFragment, String key, String name)
    {
        AREAS_BY_TITLE.put(titleFragment, new Area(key, name));
    }

    static final class Area
    {
        private final String key;
        private final String name;

        private Area(String key, String name)
        {
            this.key = key;
            this.name = name;
        }

        String getKey()
        {
            return key;
        }

        String getName()
        {
            return name;
        }
    }
}
