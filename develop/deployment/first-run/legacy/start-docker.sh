#!/bin/sh
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -eu
case "$#:${1:-}" in
    0:) ;;
    1:--recreate) set -- --force-recreate ;;
    *) echo 'Usage: ./start-docker.sh [--recreate]' >&2; exit 1 ;;
esac
bundle=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
IJOB_HOME=${IJOB_HOME:-"$(dirname -- "$bundle")/ijob-data-docker"}
export IJOB_HOME
case "$IJOB_HOME" in /*) ;; *) echo 'IJOB_HOME must be absolute' >&2; exit 1;; esac
test "$(readlink -m -- "$IJOB_HOME")" = "$IJOB_HOME" || { echo 'Use a canonical data path without symlinks' >&2; exit 1; }
test -f "$bundle/docker-images.lock" && test -f "$bundle/docker-images.compose.yaml" || { echo 'Use a complete Docker distribution built by tools/distribution/build.py.' >&2; exit 1; }
backend_image=$(sed -n 's/^IJOB_BACKEND_IMAGE=//p' "$bundle/docker-images.lock")
web_image=$(sed -n 's/^IJOB_WEB_IMAGE=//p' "$bundle/docker-images.lock")
for image in "$backend_image" "$web_image"; do
    printf '%s\n' "$image" | grep -Eq '^sha256:[0-9a-f]{64}$' || { echo 'Invalid image lock.' >&2; exit 1; }
    if ! docker image inspect "$image" >/dev/null 2>&1; then
        test -f "$bundle/ijob-docker-images.tar" || { echo 'Offline image archive missing' >&2; exit 1; }
        docker load -i "$bundle/ijob-docker-images.tar"
    fi
    docker image inspect "$image" >/dev/null
done
if [ ! -e "$IJOB_HOME" ]; then mkdir -m 700 -- "$IJOB_HOME"; fi
terminal=-i
if [ -t 0 ] && [ -t 1 ]; then terminal=-it; fi
docker run --rm "$terminal" --env "IJOB_LANG=${IJOB_LANG:-en}" --user 0:0 --network none --cap-drop ALL --cap-add CHOWN --cap-add DAC_OVERRIDE --cap-add FOWNER --security-opt no-new-privileges \
    --mount "type=bind,source=$IJOB_HOME,target=$IJOB_HOME" \
    --entrypoint java "$backend_image" -jar /opt/ijob/setup.jar init \
    --mode docker --home "$IJOB_HOME" --bundle /opt/ijob --host-uid "${SUDO_UID:-$(id -u)}" --host-gid "${SUDO_GID:-$(id -g)}"
docker compose -f "$IJOB_HOME/compose.yaml" -f "$bundle/docker-images.compose.yaml" up -d --no-build --pull never --wait --wait-timeout 180 "$@"
echo 'Open http://127.0.0.1:18080 (or the IJOB_HTTP_PORT you selected).'
