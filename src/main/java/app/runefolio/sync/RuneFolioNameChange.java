package app.runefolio.sync;

/** Local rename evidence only; never authorizes server ownership changes. */
final class RuneFolioNameChange
{
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
