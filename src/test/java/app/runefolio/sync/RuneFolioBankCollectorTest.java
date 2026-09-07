package app.runefolio.sync;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
public class RuneFolioBankCollectorTest
{
    @Test public void valuesCannotOverflowTheJsonIntegerRange()
    {
        Assert.assertEquals(0, RuneFolioBankCollector.value(-1, 100));
        Assert.assertEquals(200, RuneFolioBankCollector.value(2, 100));
        Assert.assertEquals(9_007_199_254_740_991L, RuneFolioBankCollector.value(Integer.MAX_VALUE, Integer.MAX_VALUE));
        Assert.assertEquals(9_007_199_254_740_991L, RuneFolioBankCollector.add(9_007_199_254_740_991L, 100));
    }
    @Test public void wealthHistoryIsNotSupersededByNewerSnapshots()
    {
        RuneFolioSyncEvent first = RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example", new JsonObject());
        RuneFolioSyncEvent second = RuneFolioSyncEvent.historyEvent("bank.snapshot", "Example", new JsonObject());
        Assert.assertFalse(second.supersedes(first));
    }
}
