package app.runefolio.sync;

import org.junit.Assert;
import org.junit.Test;

public class RuneFolioCatalogContractTest
{
    @Test
    public void progressCatalogsMatchTheGameTotals()
    {
        Assert.assertEquals(29, RuneFolioProgressCollector.supplementalQuestCount());
        Assert.assertEquals(182, net.runelite.api.Quest.values().length - RuneFolioProgressCollector.supplementalQuestCount());
        Assert.assertEquals(48, RuneFolioProgressCollector.trackedDiaryTierCount());
        Assert.assertEquals(655, RuneFolioCombatTaskCatalog.trackedTaskCount());
    }

    @Test
    public void collectionLogInternalIdsNormalizeToCanonicalIds()
    {
        Assert.assertEquals(12013, RuneFolioPlugin.normalizeCollectionLogItemId(29472));
        Assert.assertEquals(12014, RuneFolioPlugin.normalizeCollectionLogItemId(29474));
        Assert.assertEquals(12015, RuneFolioPlugin.normalizeCollectionLogItemId(29476));
        Assert.assertEquals(12016, RuneFolioPlugin.normalizeCollectionLogItemId(29478));
        Assert.assertEquals(25629, RuneFolioPlugin.normalizeCollectionLogItemId(24882));
        Assert.assertEquals(4151, RuneFolioPlugin.normalizeCollectionLogItemId(4151));
    }

    @Test
    public void sidePanelRemainsVisibleByDefault()
    {
        RuneFolioConfig config = new RuneFolioConfig()
        {
        };

        Assert.assertFalse(config.hideSidePanel());
    }
}
