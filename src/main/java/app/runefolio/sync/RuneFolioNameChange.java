package app.runefolio.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/** Local rename evidence only; never authorizes server ownership changes. */
final class RuneFolioNameChange
{
    static String identityKey(long accountHash)
    {
        if (accountHash == -1 || accountHash == 0) return null;
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                ("runefolio-character-v1:" + Long.toUnsignedString(accountHash))
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * True when the tracked character is no longer the one observed on this tick,
     * by display name or by account identity. Nothing is tracked while either name is null.
     */
    static boolean characterChanged(String lastName, String observedName, String activeIdentity, String observedIdentity)
    {
        return lastName != null && observedName != null
            && (!namesMatch(lastName, observedName) || !Objects.equals(activeIdentity, observedIdentity));
    }

    static boolean namesMatch(String first, String second)
    {
        return first != null && second != null && normaliseName(first).equals(normaliseName(second));
    }

    static String normaliseName(String value)
    {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
