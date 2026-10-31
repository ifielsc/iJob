#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/../support/native-common.sh"
require_unmanaged
[[ $# == 0 ]] || { echo 'Usage: ./start.sh (optional IJOB_HOME and JAVA_HOME environment variables)' >&2; exit 1; }
select_java
"$java" -jar "$bundle/ijob-setup-tool-1.0.0.jar" init --mode linux --bundle "$bundle" --home "$data_home"
require_installation
lock_control
if owned_process; then echo "Already running (PID $native_pid)."; exit 0; fi
[[ ! -L $data_home/.native-runtime.lock ]] || { echo 'Unexpected runtime lock symlink.' >&2; exit 1; }
exec 7>"$data_home/.native-runtime.lock"
flock -n 7 || { echo 'Another process owns this instance runtime.' >&2; exit 1; }

vendor=$(sed -n 's/^vendor=//p' "$data_home/installation.properties")
case "$vendor" in sqlite|mysql|postgresql|sqlserver|oracle) ;; *) echo 'Invalid database metadata.' >&2; exit 1;; esac
timeout=${IJOB_START_TIMEOUT:-120}
[[ $timeout =~ ^[1-9][0-9]*$ && $timeout -le 600 ]] || { echo 'IJOB_START_TIMEOUT must be 1-600 seconds.' >&2; exit 1; }
mkdir -p -- "$data_home/logs"
[[ -f "$bundle/support/logging-native.xml" ]] || { echo 'logging-native.xml is missing; copy the complete release folder.' >&2; exit 1; }
for name in ijob.log bootstrap.log archive; do
    [[ ! -L "$data_home/logs/$name" ]] || { echo 'Unexpected symlink in log paths.' >&2; exit 1; }
done
log="$data_home/logs/ijob.log"
bootstrap="$data_home/logs/bootstrap.log"
session="$(date +%s)-$$"
config_uri=$(file_uri "$data_home/application.properties")
(
    cd -- "$data_home/runtime"
    exec env IJOB_PLATFORM_DB="$vendor" nohup "$java" "-Dijob.native.home=$data_home" \
        "-Dloader.path=$(file_uri "$data_home/drivers/")" -jar "$bundle/ijob-starter-1.0.0.jar" --spring.profiles.active=release,native-background \
        "--spring.config.additional-location=$config_uri" "--app.web.directory=$bundle/web-dist" \
        "--logging.config=$(file_uri "$bundle/support/logging-native.xml")" \
        "--ijob.native.log-directory=$data_home/logs" "--ijob.native.log-session=$session"
) </dev/null > >(tail -c 1048576 >"$bootstrap" 2>&1 9>&-) 2>&1 9>&- &
pid=$!
ticks=$(process_ticks "$pid") || { echo "Startup failed. See $log" >&2; exit 1; }
printf '%s %s %s\n' "$pid" "$ticks" "$(cat /proc/sys/kernel/random/boot_id)" > "$pid_file.tmp.$$"
mv -T -- "$pid_file.tmp.$$" "$pid_file"
echo "Starting PID $pid. Log: $log"
for ((attempt=0; attempt<50; attempt++)); do
    if owned_process; then break; fi
    sleep 0.1
done
for ((elapsed=0; elapsed<timeout; elapsed++)); do
    owned_process || { echo "Application exited. See $log and $bootstrap" >&2; exit 1; }
    if [[ -f "$log" ]] && grep -q "\[launch=$session\].*runtimePhase=READY" "$log"; then
        echo 'Ready. Default URL: http://127.0.0.1:18080 (use your configured port if changed).'
        echo 'Run the instance ijob.sh logs or ./legacy/logs.sh to follow the application log.'
        exit 0
    fi
    sleep 1
done
echo "Startup readiness timed out; process retained. Check $log or use ./stop.sh." >&2
exit 1
