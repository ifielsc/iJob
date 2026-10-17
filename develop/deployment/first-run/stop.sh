#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/native-common.sh"
if [[ ! -d "$data_home" ]]; then echo 'Already stopped (not initialized).'; exit 0; fi
require_installation
lock_control
if ! owned_process; then echo 'Already stopped; no matching managed process was signalled.'; exit 0; fi
timeout=${IJOB_STOP_TIMEOUT:-120}
[[ $timeout =~ ^[1-9][0-9]*$ && $timeout -le 600 ]] || { echo 'IJOB_STOP_TIMEOUT must be 1-600 seconds.' >&2; exit 1; }
echo "Stopping PID $native_pid gracefully."
kill -TERM "$native_pid"
for ((elapsed=0; elapsed<timeout; elapsed++)); do
    if ! owned_process; then echo 'Stopped. Configuration, database and logs retained.'; exit 0; fi
    sleep 1
done
echo 'Still stopping; no force-kill was sent. Check logs and retry status.sh.' >&2
exit 1
