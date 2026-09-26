#!/usr/bin/env bash
# Builds the core the app bundles from an OpenFlux checkout (needs the IPC
# status, --http-proxy and --node-wizard changes) with the Go toolchain in Docker:
#
#   scripts/build-core.sh ../openfluxandroidfork [image]
#
# Writes desktopApp/resources/windows/openflux-windows-amd64.exe,
# wintun.dll (for the full tunnel) and openflux-core.version (branch@commit,
# shown under Settings → About).
set -euo pipefail

core=$(cd "${1:?path to the OpenFlux core checkout}" && pwd)
image=${2:-openflux-mobile-builder:r30}
out=$(cd "$(dirname "$0")/.." && pwd)/desktopApp/resources/windows
mkdir -p "$out"

# Docker Desktop on Windows wants C:/... paths; elsewhere pwd is fine.
host_path() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(host_path "$core"):/src:ro" \
  -v "$(host_path "$out"):/out" \
  -v ofx-gomod:/go/pkg/mod \
  -v ofx-gocache:/root/.cache/go-build \
  -w /src \
  -e GOOS=windows -e GOARCH=amd64 -e CGO_ENABLED=0 -e GOFLAGS=-buildvcs=false \
  "$image" \
  go build -trimpath -ldflags "-s -w" -o /out/openflux-windows-amd64.exe .

# Wintun for the full tunnel (--inbound=tun): the core loads wintun.dll from
# its own folder. The official build, checked against its published SHA-256.
wintun_zip=$(mktemp)
curl -fsSL -o "$wintun_zip" https://www.wintun.net/builds/wintun-0.14.1.zip
echo "07c256185d6ee3652e09fa55c0b673e2624b565e02c4b9091c79ca7d2f24ef51  $wintun_zip" | sha256sum -c - >/dev/null
unzip -p "$wintun_zip" wintun/bin/amd64/wintun.dll > "$out/wintun.dll"
rm -f "$wintun_zip"

branch=$(git -C "$core" rev-parse --abbrev-ref HEAD)
rev=$(git -C "$core" describe --always --dirty)
printf '%s@%s\n' "$branch" "$rev" > "$out/openflux-core.version"
echo "core $(cat "$out/openflux-core.version") -> $out"
