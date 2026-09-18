#!/usr/bin/env bash
# Every plugin PR ships its own version, however small the change: the three
# version fields must agree, be strictly newer than the base branch, and end
# the PR title as "(x.y.z)" - the Discord changelog title is derived from the
# PR title, so that is also what puts the version at the end of it.
#
# Usage: PR_TITLE="..." scripts/check-version-bump.sh [base-branch]
set -euo pipefail
cd "$(dirname "$0")/.."

base_branch="${1:-main}"
title="${PR_TITLE?Set PR_TITLE to the pull request title}"

fail() { echo "$*" >&2; exit 1; }

field() {
    local file="$1" pattern="$2" value
    [ -f "$file" ] || fail "$file not found"
    value="$(tr -d '\r' < "$file" | sed -n -E "$pattern")"
    [ -n "$value" ] || fail "$file: no version line matching the expected form"
    [ "$(printf '%s\n' "$value" | wc -l)" -eq 1 ] || fail "$file: more than one version line: $value"
    printf '%s' "$value"
}

properties_version="$(field runelite-plugin.properties 's/^version=([^[:space:]]+)[[:space:]]*$/\1/p')"
gradle_version="$(field build.gradle 's/^version[[:space:]]*=[[:space:]]*["'"'"']([^"'"'"']+)["'"'"'].*$/\1/p')"
client_version="$(field src/main/java/app/runefolio/sync/RuneFolioApiClient.java \
    's/^[[:space:]]*static final String CLIENT_VERSION = "([^"]+)";.*$/\1/p')"

[[ "$properties_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
    || fail "runelite-plugin.properties version '$properties_version' is not x.y.z"
[ "$properties_version" = "$gradle_version" ] && [ "$properties_version" = "$client_version" ] \
    || fail "Version fields disagree: runelite-plugin.properties=$properties_version build.gradle=$gradle_version RuneFolioApiClient.CLIENT_VERSION=$client_version"

git fetch -q --no-tags origin "$base_branch" || fail "Could not fetch $base_branch from origin"
base_version="$(git show "FETCH_HEAD:runelite-plugin.properties" | tr -d '\r' | sed -n -E 's/^version=([^[:space:]]+)[[:space:]]*$/\1/p')"
[ -n "$base_version" ] || fail "Could not read the version on $base_branch"

newest="$(printf '%s\n%s\n' "$base_version" "$properties_version" | sort -V | tail -n 1)"
if [ "$properties_version" = "$base_version" ] || [ "$newest" != "$properties_version" ]; then
    fail "Version $properties_version is not newer than $base_branch ($base_version). Every plugin PR bumps the version, even a one-line change."
fi

title="${title%"${title##*[![:space:]]}"}"
case "$title" in
    ?*"($properties_version)") ;;
    *) fail "PR title must end with ($properties_version): '$title'" ;;
esac

echo "Version $properties_version: fields agree, newer than $base_branch ($base_version), title ends with it."
