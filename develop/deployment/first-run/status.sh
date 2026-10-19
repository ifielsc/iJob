#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/native-common.sh"
if [[ ! -d "$data_home" ]]; then echo 'STOPPED (not initialized)'; exit 3; fi
require_installation
lock_control
if owned_process; then
    echo "RUNNING (PID $native_pid). Process status only; readiness details are in $data_home/logs."
else
    echo 'STOPPED (no matching managed process; any stale PID record is retained).'
    exit 3
fi
