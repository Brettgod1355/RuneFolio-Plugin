package app.runefolio.sync;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuneFolioScreenshotReceiptTest
{
    @Test
    public void acceptsOnlyMatchingReceiptIncludingDuplicate() throws Exception
    {
        UUID id = UUID.randomUUID();
        JsonObject receipt = new JsonObject();
        receipt.addProperty("screenshotId", id.toString());
        receipt.addProperty("duplicate", true);
        RuneFolioApiClient.verifyScreenshotAcknowledgement(receipt, id);
        receipt.addProperty("screenshotId", UUID.randomUUID().toString());
        try { RuneFolioApiClient.verifyScreenshotAcknowledgement(receipt, id); fail(); }
        catch (IOException expected) { }
        try { RuneFolioApiClient.verifyScreenshotAcknowledgement(new JsonObject(), id); fail(); }
        catch (IOException expected) { }
    }

    @Test
    public void retryAfterSupportsSecondsAndHttpDate()
    {
        long now = Instant.parse("2026-09-14T00:00:00Z").toEpochMilli();
        assertEquals(60_000, RuneFolioApiClient.screenshotRetryAfterMillis("60", now));
        assertEquals(120_000, RuneFolioApiClient.screenshotRetryAfterMillis("Mon, 14 Sep 2026 00:02:00 GMT", now));
        assertEquals(0, RuneFolioApiClient.screenshotRetryAfterMillis("-5", now));
        assertEquals(0, RuneFolioApiClient.screenshotRetryAfterMillis("invalid", now));
        assertEquals(0, RuneFolioApiClient.screenshotRetryAfterMillis(null, now));
        assertEquals(86_400_000, RuneFolioApiClient.screenshotRetryAfterMillis("999999999", now));
    }
}
