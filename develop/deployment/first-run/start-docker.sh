#!/bin/sh
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -eu
bundle=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IJOB_HOME=${IJOB_HOME:-"$(dirname -- "$bundle")/ijob-data-docker"}
export IJOB_HOME
case "$IJOB_HOME" in /*) ;; *) echo 'IJOB_HOME must be absolute' >&2; exit 1;; esac
test "$(readlink -m -- "$IJOB_HOME")" = "$IJOB_HOME" || { echo 'Use a canonical data path without symlinks' >&2; exit 1; }
for image in ijob-backend:1.0.0 ijob-web:1.0.0; do
    if ! docker image inspect "$image" >/dev/null 2>&1; then
        test -f "$bundle/ijob-docker-images.tar" || { echo 'Offline image archive missing' >&2; exit 1; }
        docker load -i "$bundle/ijob-docker-images.tar"
    fi
done
if [ ! -e "$IJOB_HOME" ]; then mkdir -m 700 -- "$IJOB_HOME"; fi
docker run --rm -it --env "IJOB_LANG=${IJOB_LANG:-en}" --user 0:0 --network none --cap-drop ALL --cap-add CHOWN --cap-add DAC_OVERRIDE --cap-add FOWNER --security-opt no-new-privileges \
    --mount "type=bind,source=$IJOB_HOME,target=$IJOB_HOME" \
    --entrypoint java ijob-backend:1.0.0 -jar /opt/ijob/setup.jar init \
    --mode docker --home "$IJOB_HOME" --bundle /opt/ijob --host-uid "${SUDO_UID:-$(id -u)}" --host-gid "${SUDO_GID:-$(id -g)}"
docker compose -f "$IJOB_HOME/compose.yaml" up -d --no-build --pull never
echo 'Open http://127.0.0.1:18080 (or the IJOB_HTTP_PORT you selected).'
