#!/usr/bin/env bash
# Builds, signs and publishes a GitHub release for the version in app/build.gradle.kts.
#
# Usage: scripts/release.sh
#
# Before running: bump versionCode and versionName, add a "## <versionName>" section to
# CHANGELOG.md and fastlane/metadata/android/en-US/changelogs/<versionCode>.txt, commit, push,
# and wait for CI to pass. The script checks all of this, asks for the keystore password, and
# asks once more before anything leaves this machine.
#
# Environment: LESTO_KEYSTORE (default ~/keys/lesto-release.jks), LESTO_KEY_ALIAS (default lesto).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

KEYSTORE=${LESTO_KEYSTORE:-$HOME/keys/lesto-release.jks}
KEY_ALIAS=${LESTO_KEY_ALIAS:-lesto}
OUT_DIR=$ROOT/build/release

fail() { echo "error: $*" >&2; exit 1; }

# --- Version and tools --------------------------------------------------------------------------

VERSION_NAME=$(sed -nE 's/^ *versionName = "(.*)"/\1/p' app/build.gradle.kts)
VERSION_CODE=$(sed -nE 's/^ *versionCode = ([0-9]+)/\1/p' app/build.gradle.kts)
[[ -n $VERSION_NAME && -n $VERSION_CODE ]] || fail "could not read the version from app/build.gradle.kts"
TAG=v$VERSION_NAME
APK_NAME=lesto-$VERSION_NAME.apk

SDK_DIR=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}
BUILD_TOOLS=$(ls -d "$SDK_DIR"/build-tools/* 2>/dev/null | sort -V | tail -1)
APKSIGNER=$BUILD_TOOLS/apksigner
[[ -x $APKSIGNER ]] || fail "apksigner not found; set ANDROID_HOME or sdk.dir in local.properties"
command -v gh >/dev/null || fail "the GitHub CLI (gh) is required"

# --- Checks -------------------------------------------------------------------------------------

echo "Releasing Lesto $VERSION_NAME (versionCode $VERSION_CODE)"

gh auth status >/dev/null 2>&1 || fail "gh is not logged in; run: gh auth login"
[[ -f $KEYSTORE ]] || fail "keystore not found at $KEYSTORE (set LESTO_KEYSTORE)"

[[ $(git branch --show-current) == main ]] || fail "not on main"
git diff --quiet HEAD || fail "uncommitted changes to tracked files"
git fetch --quiet origin main --tags
[[ $(git rev-parse HEAD) == $(git rev-parse origin/main) ]] || fail "main is not the same as origin/main; push or pull first"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && fail "$TAG is already released; bump versionName and versionCode"

# Android only updates to a higher versionCode, and F-Droid tells versions apart by it.
LAST_TAG=$(git tag --list 'v*' --sort=-version:refname | head -1)
if [[ -n $LAST_TAG ]]; then
    LAST_VERSION_NAME=${LAST_TAG#v}
    LAST_VERSION_CODE=$(git show "$LAST_TAG:app/build.gradle.kts" | sed -nE 's/^ *versionCode = ([0-9]+)/\1/p')
    [[ $(printf '%s\n' "$LAST_VERSION_NAME" "$VERSION_NAME" | sort -V | tail -1) == "$VERSION_NAME" ]] ||
        fail "versionName $VERSION_NAME is not newer than the last release ($LAST_VERSION_NAME)"
    ((VERSION_CODE > LAST_VERSION_CODE)) ||
        fail "versionCode $VERSION_CODE is not higher than the last release's ($LAST_VERSION_CODE); bump it"
fi

NOTES=$(awk -v version="$VERSION_NAME" '/^## / { found = ($2 == version); next } found' CHANGELOG.md)
[[ -n ${NOTES//[[:space:]]/} ]] || fail "CHANGELOG.md has no \"## $VERSION_NAME\" section"
[[ -f fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt ]] ||
    fail "missing fastlane/metadata/android/en-US/changelogs/$VERSION_CODE.txt (F-Droid shows it)"

COMMIT=$(git rev-parse HEAD)
CI_RESULT=$(gh run list --workflow CI --commit "$COMMIT" --limit 1 --json conclusion --jq '.[0].conclusion // "none"')
[[ $CI_RESULT == success ]] || fail "CI has not passed for ${COMMIT:0:7} (result: $CI_RESULT)"

# --- Build and sign -----------------------------------------------------------------------------

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
./gradlew --quiet clean assembleRelease

echo "Signing with $KEYSTORE (alias $KEY_ALIAS)"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --out "$OUT_DIR/$APK_NAME" app/build/outputs/apk/release/app-release-unsigned.apk
rm -f "$OUT_DIR/$APK_NAME.idsig" # only used for incremental installs from a computer

"$APKSIGNER" verify --min-sdk-version 26 "$OUT_DIR/$APK_NAME"
CERTIFICATE=$("$APKSIGNER" verify --print-certs "$OUT_DIR/$APK_NAME" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
(cd "$OUT_DIR" && sha256sum "$APK_NAME" >"$APK_NAME.sha256")

# --- Publish ------------------------------------------------------------------------------------

cat <<EOF

  Tag:          $TAG at ${COMMIT:0:7}
  APK:          build/release/$APK_NAME ($(du -h "$OUT_DIR/$APK_NAME" | cut -f1))
  Certificate:  $CERTIFICATE
                (must be the same for every release; compare it with the last one)

  Release notes:
$(sed 's/^/    /' <<<"$NOTES")

EOF
read -r -p "Tag, push and publish the GitHub release? [y/N] " answer
[[ $answer == [yY] ]] || { echo "Not published. The signed APK is in build/release/."; exit 0; }

git tag -a "$TAG" -m "Lesto $VERSION_NAME"
git push origin "$TAG"
gh release create "$TAG" "$OUT_DIR/$APK_NAME" "$OUT_DIR/$APK_NAME.sha256" \
    --verify-tag --title "Lesto $VERSION_NAME" --notes "$NOTES"
