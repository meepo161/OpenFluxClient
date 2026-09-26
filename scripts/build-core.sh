#!/usr/bin/env bash
# Builds the core the app bundles from an OpenFlux checkout (needs the IPC
# status, --http-proxy and --node-wizard changes) with the Go toolchain in Docker:
#
#   scripts/build-core.sh ../openfluxandroidfork [image]
#
# Writes desktopApp/resources/windows/openflux-windows-amd64.exe and
# openflux-core.version (branch@commit, shown under Settings → About).
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

branch=$(git -C "$core" rev-parse --abbrev-ref HEAD)
rev=$(git -C "$core" describe --always --dirty)
printf '%s@%s\n' "$branch" "$rev" > "$out/openflux-core.version"
echo "core $(cat "$out/openflux-core.version") -> $out"
