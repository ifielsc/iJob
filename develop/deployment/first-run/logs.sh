#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/native-common.sh"
require_installation
[[ ! -L "$data_home/logs/ijob.log" ]] || { echo 'Unexpected log symlink.' >&2; exit 1; }
exec tail -n 200 -F -- "$data_home/logs/ijob.log"
