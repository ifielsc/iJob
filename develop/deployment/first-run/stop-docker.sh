#!/bin/sh
set -eu
bundle=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IJOB_HOME=${IJOB_HOME:-"$(dirname -- "$bundle")/ijob-data-docker"}
docker compose -f "$IJOB_HOME/compose.yaml" stop
