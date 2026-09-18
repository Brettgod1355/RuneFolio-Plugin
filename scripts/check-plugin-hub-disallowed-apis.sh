#!/usr/bin/env bash
# The RuneLite Plugin Hub's automated reviewer (a private bot, separate from the
# open-source packager's disallowed-apis.txt) rejected each of these on the
# runefolio-sync submission (runelite/plugin-hub#16712). Nothing else in CI
# checks for them, so guard against any of them regressing here.
#
# Only plugin sources are scanned; tests may use raw java.io/java.nio freely.
# Lines that are entirely a comment are ignored so a rule can be described
# without tripping the check. Patterns deliberately over-match (a string
# literal naming an API fails loudly) rather than under-match.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -d src/main/java ] || { echo "src/main/java not found" >&2; exit 2; }
find src/main/java -name '*.java' | grep -q . || { echo "no plugin sources found" >&2; exit 2; }

status=0
forbid() {
    local pattern="$1" reason="$2" hits
    hits="$(grep -rn --include='*.java' -E "$pattern" src/main/java \
        | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|/\*|\*)' || true)"
    if [ -n "$hits" ]; then
        echo "$hits"
        echo "  -> $reason" >&2
        echo >&2
        status=1
    fi
}

forbid '^import java\.(io|nio\.file|awt|net)\.\*;' \
    'Wildcard imports of these packages hide disallowed APIs from this check; import classes explicitly.'
forbid 'HttpURLConnection|\.openConnection[[:space:]]*\(|\.openStream[[:space:]]*\(|java\.net\.http\.' \
    "Raw java.net networking is not allowed; @Inject and use RuneLite's OkHttpClient."
forbid 'java\.awt\.Desktop|\bDesktop\.(getDesktop|isDesktopSupported)' \
    'Desktop::browse is not allowed; use net.runelite.client.util.LinkBrowser.browse.'
forbid 'java\.nio\.file\.(Files|Path|Paths)\b|\bFiles\.[a-z]|\bPaths\.get[[:space:]]*\(|\bPath\.of[[:space:]]*\(|java\.io\.(File|FileInputStream|FileOutputStream|FileReader|FileWriter|RandomAccessFile)\b|new File[[:space:]]*\(|\bFile(Input|Output)Stream\b|\bFile(Reader|Writer)\b|\bRandomAccessFile\b|\.toFile[[:space:]]*\(|\.listFiles[[:space:]]*\(|FileChannel\.open[[:space:]]*\(|RuneLite\.[A-Z_]*(DIR|FILE|DATA)\b|\bUnchecked\.|\bZipFile[[:space:]]*\(' \
    'Direct file I/O is not allowed; use net.runelite.client.util.Filepath via Plugin.getPluginDirectory().'
forbid '\.sleep[[:space:]]*\(|LockSupport\.park' \
    'Thread.sleep is not allowed; use a ScheduledExecutorService.'
forbid '\.interrupt[[:space:]]*\(\)|isInterrupted[[:space:]]*\(\)|Thread\.interrupted[[:space:]]*\(\)|\.cancel[[:space:]]*\([[:space:]]*true[[:space:]]*\)' \
    'Thread interrupt is not allowed; use an explicit cancellation flag.'

if [ "$status" -ne 0 ]; then
    echo "Plugin Hub disallowed APIs found above." >&2
    exit 1
fi
echo "No Plugin Hub disallowed APIs found."
