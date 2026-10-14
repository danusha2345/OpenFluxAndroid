#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
revision=$(tr -d '\r\n' < core-revision.txt)
[[ "$revision" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid core-revision.txt' >&2; exit 1; }
git submodule sync -- OpenFlux
git submodule update --init -- OpenFlux
[[ -z "$(git -C OpenFlux status --porcelain)" ]] || { echo 'Core checkout has local changes' >&2; exit 1; }
git -C OpenFlux fetch origin "$revision"
git -C OpenFlux checkout --detach "$revision"
test "$(git -C OpenFlux rev-parse HEAD)" = "$revision"
