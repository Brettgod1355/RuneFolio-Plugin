#!/usr/bin/env bash
# Runs the unmodified RuneLite v3 packager from an extracted bundle directory.
set -euo pipefail
source_dir="$(realpath "${1:?Pass the plugin checkout path}")"
source_commit="$(git -C "$source_dir" rev-parse HEAD)"
repository_url="https://github.com/Brettgod1355/RuneFolio-Plugin.git"
test -f plugin-hub/runelite.version
test -f package.jar
test -f "$source_dir/runelite-plugin.properties"

# Use the already-authorized local checkout; never pass credentials to the packager.
# The descriptor remains in RuneLite's accepted format, and all build checks remain enabled.
export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0="url.file://$source_dir.insteadOf"
export GIT_CONFIG_VALUE_0="$repository_url"
export GIT_CONFIG_KEY_1="protocol.file.allow"
export GIT_CONFIG_VALUE_1=always
export GIT_TERMINAL_PROMPT=0
unset REPO_CREDS REPO_ROOT SIGNING_KEY PACKAGE_COMMIT_RANGE API_FILES_VERSION
export PACKAGE_IS_PR=true
export FORCE_BUILD=runefolio
mkdir -p plugin-hub/plugins
printf 'repository=%s\ncommit=%s\nwarning=%s\n' "$repository_url" "$source_commit" \
  'Communicates with runefolio.app to upload linked character progress and enabled optional data.' \
  > plugin-hub/plugins/runefolio
# The packager reads descriptor commit dates from local Hub history.
git -C plugin-hub add plugins/runefolio
git -C plugin-hub -c user.name="RuneFolio CI" -c user.email="ci@runefolio.invalid" \
  commit --quiet -m "Local preflight descriptor" -- plugins/runefolio
printf 'Plugin commit: %s\nRuneLite version: ' "$source_commit"
cat plugin-hub/runelite.version
printf 'Plugin Hub revision: '
git -C plugin-hub rev-parse HEAD
./prepare.sh
java -XX:+UseParallelGC -cp package.jar net.runelite.pluginhub.packager.Packager
python3 - "$source_dir" <<'PY'
import pathlib
import sys
import zipfile

source = pathlib.Path(sys.argv[1])
with zipfile.ZipFile("/tmp/jars/runefolio.jar") as jar:
    for name in ("LICENSE", "COPYRIGHT.md", "THIRD_PARTY_NOTICES.md"):
        entry = "META-INF/" + name
        if jar.namelist().count(entry) != 1 or jar.read(entry) != (source / name).read_bytes():
            raise SystemExit("Hub distribution notice missing, duplicated or changed: " + name)
    if "runelite_plugin.json" not in jar.namelist():
        raise SystemExit("Hub plugin manifest missing")
print("Official packager and Hub distribution notices passed.")
PY
