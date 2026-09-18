#!/usr/bin/env bash
# Every pull request bumps the plugin version, with no exceptions, and the
# version lives in three places that must never drift apart:
#
#   runelite-plugin.properties                          version=x.y.z
#   build.gradle                                        version = "x.y.z"
#   src/main/java/app/runefolio/sync/RuneFolioApiClient.java
#                                                       CLIENT_VERSION = "x.y.z"
#
# The pull request title must end with the new version in parentheses, e.g.
# "Fix sync-activity clipping (0.3.45)". The Discord update announcing the
# merge takes its title from the PR title, so this is also what puts the
# version at the end of that announcement.
#
# Usage:  PR_TITLE="<pull request title>" scripts/check-version-bump.sh [base]
#   base  the ref the pull request targets (default: origin/main, then main).
#         The version must be strictly newer than the version on that ref,
#         compared numerically per component, so 0.3.9 < 0.3.50.
#
# Exit status: 0 all rules hold, 1 a rule is violated, 2 usage/setup problem.
set -euo pipefail
cd "$(dirname "$0")/.."

PROPERTIES=runelite-plugin.properties
GRADLE=build.gradle
CLIENT=src/main/java/app/runefolio/sync/RuneFolioApiClient.java
SEMVER='^[0-9]+\.[0-9]+\.[0-9]+$'

fail() { echo "error: $*" >&2; exit 1; }
usage() { echo "error: $*" >&2; exit 2; }

base="${1:-}"
if [ -z "$base" ]; then
    for candidate in origin/main main; do
        if git rev-parse --verify --quiet "$candidate^{commit}" >/dev/null; then
            base="$candidate"
            break
        fi
    done
    [ -n "$base" ] || usage "no base ref found; pass one, e.g. scripts/check-version-bump.sh origin/main"
elif ! git rev-parse --verify --quiet "$base^{commit}" >/dev/null; then
    if git rev-parse --verify --quiet "origin/$base^{commit}" >/dev/null; then
        base="origin/$base"
    else
        usage "base ref '$base' does not exist locally (try: git fetch origin main)"
    fi
fi

[ -n "${PR_TITLE:-}" ] || usage "PR_TITLE is not set; run as: PR_TITLE=\"<pull request title>\" $0 [base]"

# Print the single version string a file declares. $1 is "" for the working
# tree or a git ref to read the file from; $2 is the file; $3 the sed pattern.
declared() {
    local content matches
    if [ -z "$1" ]; then
        [ -f "$2" ] || usage "$2 not found"
        content="$(cat "$2")"
    else
        content="$(git show "$1:$2" 2>/dev/null)" || usage "$2 not found on $1"
    fi
    matches="$(printf '%s\n' "$content" | sed -n "$3" | tr -d '\r')"
    case "$(printf '%s\n' "$matches" | grep -c .)" in
        1) printf '%s\n' "$matches" ;;
        0) fail "$2${1:+ on $1} declares no version" ;;
        *) fail "$2${1:+ on $1} declares more than one version:"$'\n'"$matches" ;;
    esac
}

props_pattern='s/^version=[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p'
gradle_pattern='s/^version[[:space:]]*=[[:space:]]*"\([^"]*\)"[[:space:]]*$/\1/p'
client_pattern='s/^[[:space:]]*\(static \|final \|private \|public \|package \)*String CLIENT_VERSION[[:space:]]*=[[:space:]]*"\([^"]*\)";.*$/\2/p'

props_version="$(declared "" "$PROPERTIES" "$props_pattern")"
gradle_version="$(declared "" "$GRADLE" "$gradle_pattern")"
client_version="$(declared "" "$CLIENT" "$client_pattern")"
base_version="$(declared "$base" "$PROPERTIES" "$props_pattern")"

for pair in "$PROPERTIES=$props_version" "$GRADLE=$gradle_version" "$CLIENT=$client_version" "$base:$PROPERTIES=$base_version"; do
    [[ "${pair##*=}" =~ $SEMVER ]] || fail "${pair%%=*} declares '${pair##*=}', which is not of the form x.y.z"
done

if [ "$props_version" != "$gradle_version" ] || [ "$props_version" != "$client_version" ]; then
    fail "the three version fields disagree:
  $PROPERTIES  version=$props_version
  $GRADLE      version = \"$gradle_version\"
  $CLIENT      CLIENT_VERSION = \"$client_version\"
Bump all three to the same new version."
fi
version="$props_version"

# Returns 0 when $1 is strictly newer than $2, comparing each numeric component.
newer_than() {
    local IFS=.
    local -a a b
    read -r -a a <<< "$1"
    read -r -a b <<< "$2"
    local i
    for i in 0 1 2; do
        if (( 10#${a[i]} > 10#${b[i]} )); then return 0; fi
        if (( 10#${a[i]} < 10#${b[i]} )); then return 1; fi
    done
    return 1
}

if ! newer_than "$version" "$base_version"; then
    if [ "$version" = "$base_version" ]; then
        fail "version $version is unchanged from $base; every pull request bumps the version (no exceptions)"
    fi
    fail "version $version is not newer than $base_version on $base"
fi

title="${PR_TITLE%"${PR_TITLE##*[![:space:]]}"}"
if [[ "$title" =~ \(([0-9]+\.[0-9]+\.[0-9]+)\)$ ]]; then
    title_version="${BASH_REMATCH[1]}"
    [ "$title_version" = "$version" ] || fail "the pull request title ends with ($title_version) but the code is at $version"
else
    fail "the pull request title must end with ($version), e.g. \"Fix sync-activity clipping ($version)\"; got: $title"
fi

echo "Version bump OK: $base_version on $base -> $version in all three fields; title ends with ($version)."
