#!/usr/bin/env bash
# The RuneLite Plugin Hub's reviewers rejected each of the API groups below on
# the runefolio-sync submission (runelite/plugin-hub#16712), and the Hub wiki
# forbids the language features at the end outright
# (https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features,
# "Forbidden language features"). Nothing else in CI checks for them, so guard
# against any of them regressing here.
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
# The Hub wiki's "Forbidden language features": reflection, JNI, subprocesses, runtime code loading.
forbid 'java\.lang\.reflect|java\.lang\.invoke|\.getDeclared(Methods?|Fields?|Constructors?|Annotations?)[[:space:]]*\(|\.get(Methods?|Fields?|Constructors?|Annotations?)[[:space:]]*\(|Class\.forName|\.setAccessible[[:space:]]*\(|Proxy\.newProxyInstance|\.newInstance[[:space:]]*\(|MethodHandles|\.invoke[[:space:]]*\(' \
    'Reflection is not allowed on the Plugin Hub; call things directly and keep explicit lists.'
forbid 'System\.load(Library)?[[:space:]]*\(|Runtime\.load(Library)?[[:space:]]*\(|(^|[[:space:]])native([[:space:]]+[A-Za-z_][A-Za-z0-9_$<>,]*(\[\])*){1,3}[[:space:]]*\(' \
    'JNI is not allowed on the Plugin Hub.'
forbid 'Runtime\.getRuntime[[:space:]]*\(\)[[:space:]]*\.exec|\bProcessBuilder\b|\.exec[[:space:]]*\(' \
    'Running external programs is not allowed on the Plugin Hub.'
forbid 'URLClassLoader|\.defineClass[[:space:]]*\(|javax\.script|ScriptEngine|jdk\.jshell|\.loadClass[[:space:]]*\(' \
    'Loading or running code fetched at runtime is not allowed on the Plugin Hub.'

if [ "$status" -ne 0 ]; then
    echo "Plugin Hub disallowed APIs found above." >&2
    exit 1
fi
echo "No Plugin Hub disallowed APIs found."
