package app.runefolio.sync;

/** Local rename evidence only; never authorizes server ownership changes. */
final class RuneFolioNameChange
{
    static String identityKey(long accountHash)
    {
        if (accountHash == -1 || accountHash == 0) return null;
        try
        {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                ("runefolio-character-v1:" + Long.toUnsignedString(accountHash))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        }
        catch (java.security.NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static boolean isCandidate(boolean localPlayer, long previousHash, long currentHash,
        String previousName, String currentName)
    {
        return localPlayer && previousHash != -1 && previousHash != 0 && previousHash == currentHash
            && valid(previousName) && valid(currentName)
            && !normalize(previousName).equals(normalize(currentName));
    }

    private static boolean valid(String value)
    {
        return value != null && value.trim().matches("[A-Za-z0-9 _-]{1,12}");
    }

    private static String normalize(String value)
    {
        return value.trim().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    }
}
