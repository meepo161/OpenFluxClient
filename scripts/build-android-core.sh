#!/usr/bin/env bash
# Builds the core the Android app bundles: the gomobile library from the
# mobile/ package of the OpenFlux core checkout (fork-main of meepo161/openfluxfork)
# into androidApp/libs/openflux.aar, plus openflux-core.version (branch@commit,
# shown under Settings → About).
#
#   scripts/build-android-core.sh ../OpenFlux
#
# Needs Go, gomobile (go install golang.org/x/mobile/cmd/gomobile@latest) and
# the Android SDK with NDK 27 (ANDROID_HOME / ANDROID_NDK_HOME, or the SDK in
# its default place).
set -euo pipefail

core=$(cd "${1:?path to the OpenFlux core checkout}" && pwd)
[ -d "$core/mobile" ] || { echo "$core has no mobile/ package" >&2; exit 1; }
out=$(cd "$(dirname "$0")/.." && pwd)/androidApp/libs
mkdir -p "$out"

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk" ]; then
  for guess in "${LOCALAPPDATA:-}/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -d "$guess" ] && sdk=$guess && break
  done
fi
[ -d "$sdk" ] || { echo "Android SDK not found: set ANDROID_HOME" >&2; exit 1; }
ndk=${ANDROID_NDK_HOME:-$sdk/ndk/27.0.12077973}
[ -d "$ndk" ] || { echo "Android NDK not found at $ndk: set ANDROID_NDK_HOME" >&2; exit 1; }

gomobile=${GOMOBILE_BIN:-$(command -v gomobile || true)}
[ -n "$gomobile" ] || gomobile=$(go env GOPATH)/bin/gomobile
[ -x "$gomobile" ] || [ -x "$gomobile.exe" ] || { echo "gomobile not found: go install golang.org/x/mobile/cmd/gomobile@latest" >&2; exit 1; }

export ANDROID_HOME=$sdk ANDROID_SDK_ROOT=$sdk ANDROID_NDK_HOME=$ndk
export PATH="$(dirname "$gomobile"):$PATH"
# gomobile's javac reads the generated sources (Russian doc comments) in the
# platform encoding, which is not UTF-8 on Windows.
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 ${JAVA_TOOL_OPTIONS:-}"
trap '[ -s "$out/openflux.aar" ] || rm -f "$out/openflux.aar"' EXIT

# github.com/wlynxg/anet (pulled in by the oneme/WebRTC transport) still uses
# a //go:linkname Go's linker rejects since 1.23; -checklinkname=0 lets it link.
(cd "$core/mobile" && "$gomobile" bind \
  -target=android \
  -androidapi=26 \
  -javapkg=io.openflux.bridge \
  -ldflags="-checklinkname=0 -s -w" \
  -o "$out/openflux.aar" \
  .)
rm -f "$out/openflux-sources.jar"

branch=$(git -C "$core" rev-parse --abbrev-ref HEAD)
rev=$(git -C "$core" describe --always --dirty)
printf '%s@%s\n' "$branch" "$rev" > "$out/openflux-core.version"
echo "core $(cat "$out/openflux-core.version") -> $out/openflux.aar"
