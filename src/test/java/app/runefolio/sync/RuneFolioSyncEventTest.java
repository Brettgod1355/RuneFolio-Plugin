package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioSyncEventTest
{
    @Test
    public void skillSnapshotRoundTripsThroughPersistentJson()
    {
        List<RuneFolioApiClient.SkillSnapshot> skills = Arrays.asList(
            new RuneFolioApiClient.SkillSnapshot("Attack", 99, 13_034_431),
            new RuneFolioApiClient.SkillSnapshot("Defence", 70, 737_627)
        );

        RuneFolioSyncEvent original = RuneFolioSyncEvent.skillSnapshot("Example Main", "manual", skills);
        RuneFolioSyncEvent restored = RuneFolioSyncEvent.fromJson(original.toJson());

        Assert.assertEquals(original.getId(), restored.getId());
        Assert.assertEquals("Example Main", restored.getCharacterName());
        Assert.assertTrue(restored.supersedes(original));
    }

    @Test
    public void newerSkillSnapshotOnlySupersedesTheSameCharacter()
    {
        List<RuneFolioApiClient.SkillSnapshot> skills = Arrays.asList(
            new RuneFolioApiClient.SkillSnapshot("Attack", 1, 0)
        );
        RuneFolioSyncEvent main = RuneFolioSyncEvent.skillSnapshot("Example Main", "periodic", skills);
        RuneFolioSyncEvent alt = RuneFolioSyncEvent.skillSnapshot("Example Alt", "periodic", skills);

        Assert.assertFalse(main.supersedes(alt));
    }

    @Test
    public void collectionCategorySnapshotsOnlySupersedeTheSameCategory()
    {
        JsonObject bosses = new JsonObject();
        bosses.addProperty("category", "Bosses");
        JsonObject raids = new JsonObject();
        raids.addProperty("category", "Raids");

        RuneFolioSyncEvent firstBosses = RuneFolioSyncEvent.progressSnapshot(
            RuneFolioSyncEvent.COLLECTION_CATEGORY_TYPE, "Example Main", "interface", bosses
        );
        RuneFolioSyncEvent newerBosses = RuneFolioSyncEvent.progressSnapshot(
            RuneFolioSyncEvent.COLLECTION_CATEGORY_TYPE, "Example Main", "interface", bosses
        );
        RuneFolioSyncEvent newerRaids = RuneFolioSyncEvent.progressSnapshot(
            RuneFolioSyncEvent.COLLECTION_CATEGORY_TYPE, "Example Main", "interface", raids
        );

        Assert.assertTrue(newerBosses.supersedes(firstBosses));
        Assert.assertFalse(newerRaids.supersedes(firstBosses));
    }

    @Test
    public void unlockEventsNeverSupersedeEachOther()
    {
        RuneFolioSyncEvent first = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Pet chaos elemental");
        RuneFolioSyncEvent second = RuneFolioSyncEvent.collectionLogUnlock("Example Main", "Pet chaos elemental");

        Assert.assertFalse(second.supersedes(first));
    }

    @Test
    public void lootDropsRemainSeparateAndRoundTripAllItems()
    {
        JsonArray items = new JsonArray();
        JsonObject coins = new JsonObject();
        coins.addProperty("itemId", 995);
        coins.addProperty("itemName", "Coins");
        coins.addProperty("quantity", 1250);
        coins.addProperty("unitGeValue", 1);
        coins.addProperty("unitHaValue", 1);
        coins.addProperty("totalGeValue", 1250);
        coins.addProperty("totalHaValue", 1250);
        items.add(coins);

        RuneFolioSyncEvent first = RuneFolioSyncEvent.lootDrop(
            "Example Main", "Abyssal demon", "npc", 124, 1, items, 1250, 1250
        );
        RuneFolioSyncEvent second = RuneFolioSyncEvent.lootDrop(
            "Example Main", "Abyssal demon", "npc", 124, 1, items, 1250, 1250
        );
        RuneFolioSyncEvent restored = RuneFolioSyncEvent.fromJson(first.toJson());

        Assert.assertFalse(second.supersedes(first));
        Assert.assertEquals(RuneFolioSyncEvent.LOOT_DROP_TYPE, restored.toJson().get("type").getAsString());
        Assert.assertEquals(1, restored.toJson().getAsJsonObject("payload").getAsJsonArray("items").size());
        Assert.assertEquals("event", restored.getTrigger());
    }
}
