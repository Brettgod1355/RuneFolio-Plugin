#!/usr/bin/env bash
# The RuneLite Plugin Hub's automated reviewer (a private bot, not part of
# the open-source packager's own disallowed-apis.txt list) rejects plugin
# code that opens its own java.net connections - it requires the client's
# @Inject-ed OkHttpClient instead. Found this the hard way on the runefolio
# submission's first scan; guarding against it regressing here since nothing
# else in CI checks for it.
set -euo pipefail
cd "$(dirname "$0")/.."

if grep -rln --include="*.java" -E 'HttpURLConnection|\.openConnection\(\)' src/main/java; then
    echo
    echo "Raw java.net networking found above. The RuneLite Plugin Hub does not allow" >&2
    echo "HttpURLConnection / URL.openConnection() - @Inject and use OkHttpClient instead." >&2
    exit 1
fi

echo "No disallowed raw networking APIs found."
