package app.runefolio.sync;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import org.junit.Test;

public class RuneFolioCollectionOwnershipTest
{
    @Test
    public void hostBookNeverReadsOrExportsCategoryWidgets()
    {
        AtomicInteger widgetReads = new AtomicInteger();
        Assert.assertNull(RuneFolioProgressCollector.collectionLogCategory(client(1, widgetReads), null));
        Assert.assertEquals(0, widgetReads.get());
    }

    @Test
    public void ownBookStillAttemptsNormalCategoryCapture()
    {
        AtomicInteger widgetReads = new AtomicInteger();
        Assert.assertNull(RuneFolioProgressCollector.collectionLogCategory(client(0, widgetReads), null));
        Assert.assertEquals(2, widgetReads.get());
    }

    @Test
    public void unexpectedOwnershipStateFailsClosed()
    {
        AtomicInteger widgetReads = new AtomicInteger();
        Assert.assertNull(RuneFolioProgressCollector.collectionLogCategory(client(2, widgetReads), null));
        Assert.assertEquals(0, widgetReads.get());
    }

    private Client client(int hostBook, AtomicInteger widgetReads)
    {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getVarbitValue"))
                {
                    Assert.assertEquals(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN, args[0]);
                    return hostBook;
                }
                if (method.getName().equals("getWidget"))
                {
                    widgetReads.incrementAndGet();
                    return null;
                }
                throw new AssertionError("Unexpected client call: " + method.getName());
            });
    }
}
