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

properties_version="$(sed -n 's/^version=\(.*\)$/\1/p' runelite-plugin.properties)"
gradle_version="$(sed -n 's/^version = "\(.*\)"$/\1/p' build.gradle)"
client_version="$(sed -n 's/.*CLIENT_VERSION = "\(.*\)";.*/\1/p' src/main/java/app/runefolio/sync/RuneFolioApiClient.java)"

fail() { echo "$*" >&2; exit 1; }

[[ "$properties_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || fail "runelite-plugin.properties version '$properties_version' is not x.y.z"
[ "$properties_version" = "$gradle_version" ] && [ "$properties_version" = "$client_version" ] \
    || fail "Version fields disagree: runelite-plugin.properties=$properties_version build.gradle=$gradle_version RuneFolioApiClient.CLIENT_VERSION=$client_version"

if git fetch -q --no-tags --depth=1 origin "$base_branch" 2>/dev/null; then
    base_commit=FETCH_HEAD
else
    base_commit="origin/$base_branch"
fi
base_version="$(git show "$base_commit:runelite-plugin.properties" | sed -n 's/^version=\(.*\)$/\1/p')"
[ -n "$base_version" ] || fail "Could not read the version on $base_branch"

newest="$(printf '%s\n%s\n' "$base_version" "$properties_version" | sort -V | tail -n 1)"
if [ "$properties_version" = "$base_version" ] || [ "$newest" != "$properties_version" ]; then
    fail "Version $properties_version is not newer than $base_branch ($base_version). Every plugin PR bumps the version, even a one-line change."
fi

case "$title" in
    *"($properties_version)") ;;
    *) fail "PR title must end with ($properties_version): '$title'" ;;
esac

echo "Version $properties_version: fields agree, newer than $base_branch ($base_version), title ends with it."
