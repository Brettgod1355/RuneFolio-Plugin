package app.runefolio.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class RuneFolioApiClient
{
    private static final String API_BASE = "https://runefolio.app/api";
    private static final int PROTOCOL_VERSION = 1;
    static final String CLIENT_VERSION = "0.3.41";

    private RuneFolioApiClient()
    {
    }

    static AccountLoginRequest startAccountLogin() throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("deviceLabel", "RuneLite on this computer");
        JsonObject response = post("/plugin-auth/start", body, null);
        return new AccountLoginRequest(
            response.get("requestId").getAsString(),
            response.get("pollToken").getAsString(),
            response.get("verificationUrl").getAsString()
        );
    }

    static AccountPollResult pollAccountLogin(String requestId, String pollToken) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("pollToken", pollToken);
        JsonObject response = post("/plugin-auth/poll", body, null);
        String status = response.has("status") ? response.get("status").getAsString() : "pending";
        String token = response.has("connectionToken") ? response.get("connectionToken").getAsString() : null;
        return new AccountPollResult(status, token);
    }

    static AccountHeartbeatResult accountHeartbeat(String connectionToken, String characterName, String identityKey, String previousName) throws IOException
    {
        JsonObject body = new JsonObject();
        if (characterName != null && !characterName.isBlank())
        {
            body.addProperty("characterName", characterName);
        }
        addIdentity(body, identityKey, previousName);
        JsonObject response = post("/plugin-account/heartbeat", body, connectionToken);
        boolean characterConnected = !response.has("characterConnected")
            || response.get("characterConnected").isJsonNull()
            || response.get("characterConnected").getAsBoolean();
        String setupUrl = response.has("setupUrl") && !response.get("setupUrl").isJsonNull()
            ? response.get("setupUrl").getAsString()
            : null;
        boolean pro = response.has("pro") && response.get("pro").getAsBoolean();
        return new AccountHeartbeatResult(characterConnected, setupUrl, pro);
    }

    static void disconnectAccount(String connectionToken) throws IOException
    {
        post("/plugin-account/disconnect", new JsonObject(), connectionToken);
    }

    static ConnectionResult exchange(String temporaryCode, String characterName, String connectionLabel) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("code", temporaryCode);
        body.addProperty("characterName", characterName);
        body.addProperty("connectionLabel", connectionLabel);

        JsonObject response = post("/plugin-links/exchange", body, null);
        JsonObject character = response.getAsJsonObject("character");
        return new ConnectionResult(
            response.get("connectionToken").getAsString(),
            character.get("name").getAsString(),
            response.has("pro") && response.get("pro").getAsBoolean()
        );
    }

    static ConnectionResult heartbeat(String connectionToken, String characterName, String identityKey, String previousName) throws IOException
    {
        JsonObject body = new JsonObject();
        body.addProperty("characterName", characterName);
        addIdentity(body, identityKey, previousName);
        JsonObject response = post("/plugin-links/heartbeat", body, connectionToken);
        JsonObject character = response.getAsJsonObject("character");
        return new ConnectionResult(connectionToken, character.get("name").getAsString(),
            response.has("pro") && response.get("pro").getAsBoolean());
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

        JsonObject response = post("/plugin-sync", body, connectionToken);
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
        String boundary = "RuneFolio-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream(jpeg.length + 2048);
        writeField(body, boundary, "eventId", eventId.toString());
        writeField(body, boundary, "characterName", characterName);
        if (identityKey != null) writeField(body, boundary, "identityKey", identityKey);
        writeField(body, boundary, "category", category);
        writeField(body, boundary, "caption", caption);
        writeField(body, boundary, "occurredAt", occurredAt);
        writeAscii(body, "--" + boundary + "\r\n");
        writeAscii(body, "Content-Disposition: form-data; name=\"file\"; filename=\"runefolio.jpg\"\r\n");
        writeAscii(body, "Content-Type: image/jpeg\r\n\r\n");
        body.write(jpeg);
        writeAscii(body, "\r\n--" + boundary + "--\r\n");

        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + "/plugin-screenshots").openConnection();
        try
        {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + connectionToken);
            byte[] payload = body.toByteArray();
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream())
            {
                output.write(payload);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
            {
                InputStream error = connection.getErrorStream();
                if (error != null) error.close();
                throw new RuneFolioScreenshotSpool.UploadException(status,
                    screenshotRetryAfterMillis(connection.getHeaderField("Retry-After"), System.currentTimeMillis()));
            }
            verifyScreenshotAcknowledgement(readJsonResponse(connection.getInputStream()), eventId);
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static void writeField(ByteArrayOutputStream output, String boundary, String name, String value)
        throws IOException
    {
        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        writeAscii(output, "\r\n");
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

    private static void writeAscii(ByteArrayOutputStream output, String value) throws IOException
    {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
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

    private static JsonObject post(String path, JsonObject body, String connectionToken) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        try
        {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (connectionToken != null)
            {
                connection.setRequestProperty("Authorization", "Bearer " + connectionToken);
            }

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
            JsonObject json = readJsonResponse(stream);

            if (status < 200 || status >= 300)
            {
                String message = json.has("message")
                    ? json.get("message").getAsString()
                    : (json.has("error") ? json.get("error").getAsString() : "RuneFolio rejected the connection.");
                throw new IOException(message);
            }

            return json;
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static JsonObject readJsonResponse(InputStream stream) throws IOException
    {
        String response = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
        private final boolean pro;

        ConnectionResult(String connectionToken, String characterName, boolean pro)
        {
            this.connectionToken = connectionToken;
            this.characterName = characterName;
            this.pro = pro;
        }

        String getConnectionToken()
        {
            return connectionToken;
        }

        String getCharacterName()
        {
            return characterName;
        }

        boolean isPro()
        {
            return pro;
        }

    }

    static final class AccountHeartbeatResult
    {
        private final boolean characterConnected;
        private final String setupUrl;
        private final boolean pro;

        AccountHeartbeatResult(boolean characterConnected, String setupUrl, boolean pro)
        {
            this.characterConnected = characterConnected;
            this.setupUrl = setupUrl;
            this.pro = pro;
        }

        boolean isCharacterConnected()
        {
            return characterConnected;
        }

        String getSetupUrl()
        {
            return setupUrl;
        }

        boolean isPro()
        {
            return pro;
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
