package app.runefolio.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.ByteString;
import org.junit.Test;

import static org.junit.Assert.*;

/** Shapes every outbound request through an application interceptor: no sockets, no DNS. */
public class RuneFolioApiClientRequestTest
{
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final class Recorded
    {
        Request request;
        ByteString body;
        int connectTimeout, readTimeout;

        String text()
        {
            return body.utf8();
        }
    }

    private static OkHttpClient client(Recorded recorded, int status, String json, String... headers)
    {
        return client(recorded, status, ResponseBody.create(JSON, json), headers);
    }

    private static OkHttpClient client(Recorded recorded, int status, ResponseBody responseBody, String... headers)
    {
        return new OkHttpClient.Builder().addInterceptor(chain ->
        {
            Request request = chain.request();
            recorded.request = request;
            recorded.connectTimeout = chain.connectTimeoutMillis();
            recorded.readTimeout = chain.readTimeoutMillis();
            Buffer buffer = new Buffer();
            if (request.body() != null) request.body().writeTo(buffer);
            recorded.body = buffer.readByteString();
            Response.Builder response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("synthetic").body(responseBody);
            for (int i = 0; i + 1 < headers.length; i += 2) response.header(headers[i], headers[i + 1]);
            return response.build();
        }).build();
    }

    private static ResponseBody unknownLength(String text)
    {
        return ResponseBody.create(JSON, -1L, new Buffer().writeUtf8(text));
    }

    @Test
    public void scopedClientNeverFollowsRedirectsAndBoundsEveryTimeout()
    {
        OkHttpClient base = new OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build();
        OkHttpClient scoped = RuneFolioApiClient.scopedClient(base, 10, 20);
        assertFalse(scoped.followRedirects());
        assertFalse(scoped.followSslRedirects());
        assertEquals(10_000, scoped.connectTimeoutMillis());
        assertEquals(20_000, scoped.readTimeoutMillis());
        assertTrue("the base client is left untouched", base.followRedirects());
    }

    @Test
    public void syncEventsSendsBearerJsonWithBoundedTimeoutsAndMapsEveryAcknowledgementClass() throws Exception
    {
        Recorded recorded = new Recorded();
        RuneFolioSyncEvent event = RuneFolioSyncEvent.collectionLogUnlock("Example", "Abyssal whip");
        RuneFolioApiClient.BatchSyncResult result = RuneFolioApiClient.syncEvents(
            client(recorded, 200, "{\"acceptedEventIds\":[\"" + event.getId() + "\"],\"duplicateEventIds\":[\"dup\"],"
                + "\"discardedEventIds\":[\"gone\"],\"rejectedEvents\":[{\"retryable\":true},{\"retryable\":false},{}],"
                + "\"revoke\":false,\"requestFullSync\":true}"),
            "synthetic-token", List.of(event));
        assertEquals("https://runefolio.app/api/plugin-sync", recorded.request.url().toString());
        assertEquals("POST", recorded.request.method());
        assertEquals("Bearer synthetic-token", recorded.request.header("Authorization"));
        assertEquals("application/json", recorded.request.header("Accept"));
        assertEquals("json", recorded.request.body().contentType().subtype());
        assertEquals(10_000, recorded.connectTimeout);
        assertEquals(10_000, recorded.readTimeout);
        JsonObject body = new JsonParser().parse(recorded.text()).getAsJsonObject();
        assertEquals(1, body.get("protocolVersion").getAsInt());
        assertEquals(RuneFolioApiClient.CLIENT_VERSION, body.get("clientVersion").getAsString());
        assertEquals(event.getId(), body.getAsJsonArray("events").get(0).getAsJsonObject().get("id").getAsString());
        assertFalse("token must travel only in the header", recorded.text().contains("synthetic-token"));
        assertEquals(List.of(event.getId(), "dup", "gone"), result.getAcknowledgedEventIds());
        assertEquals(List.of(event.getId(), "dup"), result.getSuccessfulEventIds());
        assertEquals(2, result.getRetryableRejectedCount());
        assertTrue(result.shouldRequestFullSync());
        assertFalse(result.shouldRevoke());
    }

    @Test
    public void failuresSurfaceTheServerMessageAndUnauthenticatedCallsCarryNoToken() throws Exception
    {
        Recorded recorded = new Recorded();
        try
        {
            RuneFolioApiClient.exchange(client(recorded, 403, "{\"message\":\"Code expired\"}"), "RF-CODE", "Example", "RuneLite");
            fail("403 must fail");
        }
        catch (IOException expected) { assertEquals("Code expired", expected.getMessage()); }
        assertNull(recorded.request.header("Authorization"));
        assertEquals("RF-CODE", new JsonParser().parse(recorded.text()).getAsJsonObject().get("code").getAsString());
        RuneFolioApiClient.startAccountLogin(client(recorded, 200,
            "{\"requestId\":\"r\",\"pollToken\":\"p\",\"verificationUrl\":\"https://runefolio.app/verify\"}"));
        assertNull(recorded.request.header("Authorization"));
        try
        {
            RuneFolioApiClient.pollAccountLogin(client(recorded, 200, "<html>"), "r", "p");
            fail("HTML must fail");
        }
        catch (IOException expected) { assertEquals("RuneFolio returned an unreadable response.", expected.getMessage()); }
        try
        {
            RuneFolioApiClient.disconnectAccount(client(recorded, 500, ""), "tok");
            fail("500 must fail");
        }
        catch (IOException expected) { assertEquals("RuneFolio rejected the connection.", expected.getMessage()); }
        assertEquals("Bearer tok", recorded.request.header("Authorization"));
        try
        {
            RuneFolioApiClient.heartbeat(client(recorded, 400, "{\"error\":\"Bad request\"}"), "tok", "Example", null, null);
            fail("400 must fail");
        }
        catch (IOException expected) { assertEquals("Bad request", expected.getMessage()); }
    }

    @Test
    public void accountHeartbeatSendsIdentityOnlyWhenKnownAndDefaultsToConnected() throws Exception
    {
        Recorded recorded = new Recorded();
        RuneFolioApiClient.AccountHeartbeatResult result = RuneFolioApiClient.accountHeartbeat(
            client(recorded, 200, "{}"), "tok", "Example", null, null);
        assertEquals("https://runefolio.app/api/plugin-account/heartbeat", recorded.request.url().toString());
        assertEquals("Bearer tok", recorded.request.header("Authorization"));
        JsonObject body = new JsonParser().parse(recorded.text()).getAsJsonObject();
        assertEquals("Example", body.get("characterName").getAsString());
        assertFalse(body.has("identityKey"));
        assertFalse(body.has("previousName"));
        assertTrue(result.isCharacterConnected());
        assertNull(result.getSetupUrl());

        String identity = "a".repeat(64);
        result = RuneFolioApiClient.accountHeartbeat(
            client(recorded, 200, "{\"characterConnected\":false,\"setupUrl\":\"https://runefolio.app/setup\",\"pro\":true}"),
            "tok", "", identity, "Old Name");
        body = new JsonParser().parse(recorded.text()).getAsJsonObject();
        assertFalse("blank names are not sent", body.has("characterName"));
        assertEquals(identity, body.get("identityKey").getAsString());
        assertEquals("Old Name", body.get("previousName").getAsString());
        assertFalse(result.isCharacterConnected());
        assertEquals("https://runefolio.app/setup", result.getSetupUrl());

        result = RuneFolioApiClient.accountHeartbeat(client(recorded, 200, "{\"characterConnected\":null}"), "tok", "Example", null, null);
        assertTrue(result.isCharacterConnected());
    }

    @Test
    public void screenshotUploadIsMultipartBearerWithLongerReadTimeoutAndRetryAfterMapping() throws Exception
    {
        Recorded recorded = new Recorded();
        UUID id = UUID.randomUUID();
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, 0, (byte) 0xff, (byte) 0xd9};
        try
        {
            RuneFolioApiClient.uploadScreenshot(client(recorded, 429, "{}", "Retry-After", "120"), "secret-token",
                "Example", id, "a".repeat(64), "level_up", "Example level 2", "2026-01-01T00:00:00Z", jpeg);
            fail("429 must fail");
        }
        catch (RuneFolioScreenshotSpool.UploadException expected)
        {
            assertEquals(429, expected.status);
            assertEquals(120_000, expected.retryAfterMillis);
            assertTrue(expected.retryable());
        }
        assertEquals("https://runefolio.app/api/plugin-screenshots", recorded.request.url().toString());
        assertEquals("POST", recorded.request.method());
        assertEquals("Bearer secret-token", recorded.request.header("Authorization"));
        assertEquals("application/json", recorded.request.header("Accept"));
        assertEquals(10_000, recorded.connectTimeout);
        assertEquals(20_000, recorded.readTimeout);
        MediaType contentType = recorded.request.body().contentType();
        assertEquals("multipart", contentType.type());
        assertEquals("form-data", contentType.subtype());
        String header = contentType.toString();
        assertTrue(header, header.startsWith("multipart/form-data; boundary="));
        String boundary = header.substring("multipart/form-data; boundary=".length());
        assertFalse(boundary.isEmpty());
        String text = recorded.text();
        assertTrue(text.startsWith("--" + boundary + "\r\n"));
        assertTrue(text.endsWith("\r\n--" + boundary + "--\r\n"));
        int eventIdAt = text.indexOf("Content-Disposition: form-data; name=\"eventId\"");
        int characterAt = text.indexOf("Content-Disposition: form-data; name=\"characterName\"");
        int identityAt = text.indexOf("Content-Disposition: form-data; name=\"identityKey\"");
        int categoryAt = text.indexOf("Content-Disposition: form-data; name=\"category\"");
        int captionAt = text.indexOf("Content-Disposition: form-data; name=\"caption\"");
        int occurredAt = text.indexOf("Content-Disposition: form-data; name=\"occurredAt\"");
        int fileAt = text.indexOf("Content-Disposition: form-data; name=\"file\"; filename=\"runefolio.jpg\"\r\nContent-Type: image/jpeg\r\n");
        assertTrue("fields keep their wire order",
            eventIdAt >= 0 && eventIdAt < characterAt && characterAt < identityAt && identityAt < categoryAt
                && categoryAt < captionAt && captionAt < occurredAt && occurredAt < fileAt);
        assertTrue(text.contains("\r\n\r\n" + id + "\r\n"));
        assertTrue(text.contains("\r\n\r\n" + "a".repeat(64) + "\r\n"));
        assertTrue(text.contains("\r\n\r\nExample\r\n"));
        assertTrue(text.contains("\r\n\r\nlevel_up\r\n"));
        assertTrue(text.contains("\r\n\r\nExample level 2\r\n"));
        assertTrue(text.contains("\r\n\r\n2026-01-01T00:00:00Z\r\n"));
        int jpegAt = recorded.body.indexOf(ByteString.of(jpeg));
        assertTrue("the JPEG bytes travel verbatim after the file part header", jpegAt > fileAt);
        assertFalse("token must travel only in the header", text.contains("secret-token"));

        RuneFolioApiClient.uploadScreenshot(client(recorded, 200, "{\"screenshotId\":\"" + id + "\",\"duplicate\":true}"),
            "secret-token", "Example", id, null, "level_up", "Example level 2", "2026-01-01T00:00:00Z", jpeg);
        assertFalse(recorded.text().contains("identityKey"));
        try
        {
            RuneFolioApiClient.uploadScreenshot(client(recorded, 200, "{\"screenshotId\":\"" + UUID.randomUUID() + "\"}"),
                "secret-token", "Example", id, null, "level_up", "Example level 2", "2026-01-01T00:00:00Z", jpeg);
            fail("mismatched receipt must fail");
        }
        catch (IOException expected) { assertFalse(expected instanceof RuneFolioScreenshotSpool.UploadException); }
    }

    @Test
    public void jsonResponsesAreCappedAtOneMebibyteWhetherOrNotTheLengthIsKnown() throws Exception
    {
        Recorded recorded = new Recorded();
        String padding = "x".repeat(1024 * 1024);
        String oversized = "{\"status\":\"pending\",\"pad\":\"" + padding + "\"}";
        String fitting = "{\"status\":\"pending\",\"pad\":\"" + "x".repeat(1024 * 1024 - 64) + "\"}";
        assertFalse(RuneFolioApiClient.pollAccountLogin(client(recorded, 200, fitting), "r", "p").isApproved());
        assertFalse(RuneFolioApiClient.pollAccountLogin(client(recorded, 200, unknownLength(fitting)), "r", "p").isApproved());
        try
        {
            RuneFolioApiClient.pollAccountLogin(client(recorded, 200, oversized), "r", "p");
            fail("a known oversized body must be rejected");
        }
        catch (IOException expected) { assertEquals("RuneFolio returned an oversized response.", expected.getMessage()); }
        try
        {
            RuneFolioApiClient.pollAccountLogin(client(recorded, 200, unknownLength(oversized)), "r", "p");
            fail("a streamed oversized body must be rejected");
        }
        catch (IOException expected) { assertEquals("RuneFolio returned an oversized response.", expected.getMessage()); }
        try
        {
            RuneFolioApiClient.uploadScreenshot(client(recorded, 200, unknownLength(oversized)), "tok", "Example",
                UUID.randomUUID(), null, "level_up", "Example level 2", "2026-01-01T00:00:00Z",
                new byte[] {(byte) 0xff, (byte) 0xd8, 0, (byte) 0xff, (byte) 0xd9});
            fail("an oversized acknowledgement must be rejected");
        }
        catch (IOException expected) { assertEquals("RuneFolio returned an oversized response.", expected.getMessage()); }
    }

    @Test
    public void readBoundedReturnsNullPastTheCapAndEmptyForNoBody() throws Exception
    {
        assertArrayEquals(new byte[0], RuneFolioApiClient.readBounded(null, 16));
        assertEquals("abc", new String(RuneFolioApiClient.readBounded(ResponseBody.create(JSON, "abc"), 3), "UTF-8"));
        assertNull(RuneFolioApiClient.readBounded(ResponseBody.create(JSON, "abcd"), 3));
        assertNull(RuneFolioApiClient.readBounded(unknownLength("abcd"), 3));
        assertEquals("abcd", new String(RuneFolioApiClient.readBounded(unknownLength("abcd"), 4), "UTF-8"));
    }

    @Test
    public void manifestFetchIsAnonymousShortTimeoutAndFailsClosed() throws Exception
    {
        Recorded recorded = new Recorded();
        String valid = "{\"schemaVersion\":1,\"revision\":\"r1\",\"combatTaskCount\":655,\"questCapeExclusions\":[\"DADDYS_HOME\"]}";
        assertNotNull(RuneFolioCollectorManifest.fetch(client(recorded, 200, valid)));
        assertEquals("https://runefolio.app/api/collector-manifest", recorded.request.url().toString());
        assertEquals("GET", recorded.request.method());
        assertNull(recorded.request.header("Authorization"));
        assertEquals(5_000, recorded.connectTimeout);
        assertEquals(5_000, recorded.readTimeout);
        assertNull(RuneFolioCollectorManifest.fetch(client(recorded, 500, valid)));
        assertNull(RuneFolioCollectorManifest.fetch(client(recorded, 200, "[]")));
        assertNull(RuneFolioCollectorManifest.fetch(client(recorded, 200, "not json")));
        String padded = "{\"schemaVersion\":1,\"revision\":\"r1\",\"combatTaskCount\":655,\"questCapeExclusions\":[],"
            + "\"pad\":\"" + "x".repeat(70_000) + "\"}";
        assertNull(RuneFolioCollectorManifest.fetch(client(recorded, 200, padded)));
        assertNull(RuneFolioCollectorManifest.fetch(client(recorded, 200, unknownLength(padded))));
    }
}
