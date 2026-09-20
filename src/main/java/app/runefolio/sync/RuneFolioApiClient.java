package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class RuneFolioApiClient
{
    private static final String API_BASE = "https://runefolio.app/api";
    private static final int PROTOCOL_VERSION = 1;
    static final String CLIENT_VERSION = "0.3.59";
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int READ_BUFFER_BYTES = 4096;
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final MediaType JPEG = MediaType.parse("image/jpeg");

    private RuneFolioApiClient()
    {
    }

    static AccountLoginRequest startAccountLogin(OkHttpClient client) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("deviceLabel", "RuneLite on this computer");
        JsonObject response = post(client, "/plugin-auth/start", body, null);
        return new AccountLoginRequest(
            response.get("requestId").getAsString(),
            response.get("pollToken").getAsString(),
            response.get("verificationUrl").getAsString()
        );
    }

    static AccountPollResult pollAccountLogin(OkHttpClient client, String requestId, String pollToken) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("pollToken", pollToken);
        JsonObject response = post(client, "/plugin-auth/poll", body, null);
        String status = response.has("status") ? response.get("status").getAsString() : "pending";
        String token = response.has("connectionToken") ? response.get("connectionToken").getAsString() : null;
        return new AccountPollResult(status, token);
    }

    static AccountHeartbeatResult accountHeartbeat(OkHttpClient client, String connectionToken, String characterName, String identityKey, String previousName) throws IOException
    {
        JsonObject body = new JsonObject();
        if (characterName != null && !characterName.isBlank())
        {
            body.addProperty("characterName", characterName);
        }
        addIdentity(body, identityKey, previousName);
        JsonObject response = post(client, "/plugin-account/heartbeat", body, connectionToken);
        boolean characterConnected = !response.has("characterConnected")
            || response.get("characterConnected").isJsonNull()
            || response.get("characterConnected").getAsBoolean();
        String setupUrl = response.has("setupUrl") && !response.get("setupUrl").isJsonNull()
            ? response.get("setupUrl").getAsString()
            : null;
        return new AccountHeartbeatResult(characterConnected, setupUrl);
    }

    static void disconnectAccount(OkHttpClient client, String connectionToken) throws IOException
    {
        post(client, "/plugin-account/disconnect", new JsonObject(), connectionToken);
    }

    static ConnectionResult exchange(OkHttpClient client, String temporaryCode, String characterName, String connectionLabel) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("code", temporaryCode);
        body.addProperty("characterName", characterName);
        body.addProperty("connectionLabel", connectionLabel);

        JsonObject response = post(client, "/plugin-links/exchange", body, null);
        JsonObject character = response.getAsJsonObject("character");
        return new ConnectionResult(
            response.get("connectionToken").getAsString(),
            character.get("name").getAsString()
        );
    }

    static ConnectionResult heartbeat(OkHttpClient client, String connectionToken, String characterName, String identityKey, String previousName) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("characterName", characterName);
        addIdentity(body, identityKey, previousName);
        JsonObject response = post(client, "/plugin-links/heartbeat", body, connectionToken);
        JsonObject character = response.getAsJsonObject("character");
        return new ConnectionResult(connectionToken, character.get("name").getAsString());
    }

    private static void addIdentity(JsonObject body, String identityKey, String previousName)
    {
        if (identityKey != null)
        {
            body.addProperty("identityKey", identityKey);
            body.addProperty("previousName", previousName);
        }
    }

    static BatchSyncResult syncEvents(
        OkHttpClient client,
        String connectionToken,
        List<RuneFolioSyncEvent> events
    ) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("protocolVersion", PROTOCOL_VERSION);
        body.addProperty("clientVersion", CLIENT_VERSION);

        JsonArray eventArray = new JsonArray();
        for (RuneFolioSyncEvent event : events)
        {
            eventArray.add(event.toJson());
        }
        body.add("events", eventArray);

        JsonObject response = post(client, "/plugin-sync", body, connectionToken);
        List<String> successful = new ArrayList<>();
        addStrings(response, "acceptedEventIds", successful);
        addStrings(response, "duplicateEventIds", successful);
        List<String> acknowledged = new ArrayList<>(successful);
        addStrings(response, "discardedEventIds", acknowledged);
        int retryableRejectedCount = 0;
        if (response.has("rejectedEvents") && response.get("rejectedEvents").isJsonArray())
        {
            for (com.google.gson.JsonElement element : response.getAsJsonArray("rejectedEvents"))
            {
                if (element.isJsonObject())
                {
                    JsonObject rejected = element.getAsJsonObject();
                    if (!rejected.has("retryable") || rejected.get("retryable").getAsBoolean())
                    {
                        retryableRejectedCount++;
                    }
                }
            }
        }
        return new BatchSyncResult(
            acknowledged,
            successful,
            response.has("revoke") && response.get("revoke").getAsBoolean(),
            response.has("requestFullSync") && response.get("requestFullSync").getAsBoolean(),
            retryableRejectedCount
        );
    }

    static void uploadScreenshot(
        OkHttpClient client,
        String connectionToken,
        String characterName,
        UUID eventId,
        String identityKey,
        String category,
        String caption,
        String occurredAt,
        byte[] jpeg
    ) throws IOException
    {
        MultipartBody.Builder form = new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("eventId", eventId.toString())
            .addFormDataPart("characterName", characterName);
        if (identityKey != null) form.addFormDataPart("identityKey", identityKey);
        RequestBody requestBody = form
            .addFormDataPart("category", category)
            .addFormDataPart("caption", caption)
            .addFormDataPart("occurredAt", occurredAt)
            .addFormDataPart("file", "runefolio.jpg", RequestBody.create(JPEG, jpeg))
            .build();
        Request request = new Request.Builder()
            .url(API_BASE + "/plugin-screenshots")
            .post(requestBody)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + connectionToken)
            .build();
        OkHttpClient scoped = scopedClient(client, 10, 20);
        try (Response response = scoped.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new RuneFolioScreenshotSpool.UploadException(response.code(),
                    screenshotRetryAfterMillis(response.header("Retry-After"), System.currentTimeMillis()));
            }
            verifyScreenshotAcknowledgement(readJsonResponse(response.body()), eventId);
        }
    }

    static void verifyScreenshotAcknowledgement(JsonObject json, UUID eventId) throws IOException
    {
        if (json == null || !json.has("screenshotId") || !json.get("screenshotId").isJsonPrimitive()
            || !eventId.toString().equals(json.get("screenshotId").getAsString()))
            throw new IOException("Screenshot acknowledgement did not match its event");
    }

    static long screenshotRetryAfterMillis(String header, long now)
    {
        if (header == null) return 0;
        try
        {
            long seconds = Long.parseLong(header.trim());
            return Math.max(0, Math.min(86_400, seconds)) * 1000;
        }
        catch (RuntimeException notSeconds)
        {
            try
            {
                long until = java.time.ZonedDateTime.parse(header,
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                return Math.max(0, Math.min(86_400_000, until - now));
            }
            catch (RuntimeException invalid) { return 0; }
        }
    }

    private static void addStrings(JsonObject response, String key, List<String> destination)
    {
        if (!response.has(key) || !response.get(key).isJsonArray())
        {
            return;
        }
        for (com.google.gson.JsonElement element : response.getAsJsonArray(key))
        {
            if (element.isJsonPrimitive())
            {
                destination.add(element.getAsString());
            }
        }
    }

    private static JsonObject post(OkHttpClient client, String path, JsonObject body, String connectionToken) throws IOException
    {
        RequestBody requestBody = RequestBody.create(JSON, body.toString().getBytes(StandardCharsets.UTF_8));
        Request.Builder builder = new Request.Builder()
            .url(API_BASE + path)
            .post(requestBody)
            .header("Accept", "application/json");
        if (connectionToken != null)
        {
            builder.header("Authorization", "Bearer " + connectionToken);
        }
        OkHttpClient scoped = scopedClient(client, 10, 10);

        try (Response response = scoped.newCall(builder.build()).execute())
        {
            JsonObject json = readJsonResponse(response.body());

            if (!response.isSuccessful())
            {
                String message = json.has("message")
                    ? json.get("message").getAsString()
                    : (json.has("error") ? json.get("error").getAsString() : "RuneFolio rejected the connection.");
                throw new IOException(message);
            }

            return json;
        }
    }

    /** Every RuneFolio call talks to one fixed origin, so a redirect is never a valid answer. */
    static OkHttpClient scopedClient(OkHttpClient client, int connectSeconds, int readSeconds)
    {
        return client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .build();
    }

    /** Null when the body is larger than {@code maxBytes}; the length header alone is not trusted. */
    static byte[] readBounded(ResponseBody body, int maxBytes) throws IOException
    {
        if (body == null) return new byte[0];
        if (body.contentLength() > maxBytes) return null;
        try (InputStream input = body.byteStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                if (bytes.size() + read > maxBytes) return null;
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    private static JsonObject readJsonResponse(ResponseBody body) throws IOException
    {
        byte[] bytes = readBounded(body, MAX_JSON_BYTES);
        if (bytes == null) throw new IOException("RuneFolio returned an oversized response.");
        Charset charset = body != null && body.contentType() != null
            ? body.contentType().charset(StandardCharsets.UTF_8)
            : StandardCharsets.UTF_8;
        String response = new String(bytes, charset);
        try
        {
            return response.isBlank() ? new JsonObject() : new JsonParser().parse(response).getAsJsonObject();
        }
        catch (RuntimeException exception)
        {
            throw new IOException("RuneFolio returned an unreadable response.", exception);
        }
    }

    static final class SkillSnapshot
    {
        private final String name;
        private final int level;
        private final int experience;

        SkillSnapshot(String name, int level, int experience)
        {
            this.name = name;
            this.level = level;
            this.experience = experience;
        }

        String getName()
        {
            return name;
        }

        int getLevel()
        {
            return level;
        }

        int getExperience()
        {
            return experience;
        }
    }

    static final class ConnectionResult
    {
        private final String connectionToken;
        private final String characterName;

        ConnectionResult(String connectionToken, String characterName)
        {
            this.connectionToken = connectionToken;
            this.characterName = characterName;
        }

        String getConnectionToken()
        {
            return connectionToken;
        }

        String getCharacterName()
        {
            return characterName;
        }
    }

    static final class AccountHeartbeatResult
    {
        private final boolean characterConnected;
        private final String setupUrl;

        AccountHeartbeatResult(boolean characterConnected, String setupUrl)
        {
            this.characterConnected = characterConnected;
            this.setupUrl = setupUrl;
        }

        boolean isCharacterConnected()
        {
            return characterConnected;
        }

        String getSetupUrl()
        {
            return setupUrl;
        }
    }

    static final class AccountLoginRequest
    {
        private final String requestId;
        private final String pollToken;
        private final String verificationUrl;

        AccountLoginRequest(String requestId, String pollToken, String verificationUrl)
        {
            this.requestId = requestId;
            this.pollToken = pollToken;
            this.verificationUrl = verificationUrl;
        }

        String getRequestId()
        {
            return requestId;
        }

        String getPollToken()
        {
            return pollToken;
        }

        String getVerificationUrl()
        {
            return verificationUrl;
        }
    }

    static final class AccountPollResult
    {
        private final String status;
        private final String connectionToken;

        AccountPollResult(String status, String connectionToken)
        {
            this.status = status;
            this.connectionToken = connectionToken;
        }

        boolean isApproved()
        {
            return "approved".equals(status) && connectionToken != null && !connectionToken.isBlank();
        }

        String getConnectionToken()
        {
            return connectionToken;
        }
    }

    static final class BatchSyncResult
    {
        private final List<String> acknowledgedEventIds;
        private final List<String> successfulEventIds;
        private final boolean revoke;
        private final boolean requestFullSync;
        private final int retryableRejectedCount;

        BatchSyncResult(
            List<String> acknowledgedEventIds,
            List<String> successfulEventIds,
            boolean revoke,
            boolean requestFullSync,
            int retryableRejectedCount
        )
        {
            this.acknowledgedEventIds = acknowledgedEventIds;
            this.successfulEventIds = successfulEventIds;
            this.revoke = revoke;
            this.requestFullSync = requestFullSync;
            this.retryableRejectedCount = retryableRejectedCount;
        }

        List<String> getAcknowledgedEventIds()
        {
            return acknowledgedEventIds;
        }

        List<String> getSuccessfulEventIds()
        {
            return successfulEventIds;
        }

        boolean shouldRevoke()
        {
            return revoke;
        }

        boolean shouldRequestFullSync()
        {
            return requestFullSync;
        }

        int getRetryableRejectedCount()
        {
            return retryableRejectedCount;
        }
    }
}
