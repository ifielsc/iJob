#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -euo pipefail
umask 077

bundle=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
data_home=${IJOB_HOME:-"$(dirname -- "$bundle")/ijob-data-linux"}
case "$data_home" in /*) ;; *) echo 'IJOB_HOME must be an absolute path.' >&2; exit 1;; esac
[[ $(readlink -m -- "$data_home") == "$data_home" ]] || { echo 'Use a canonical data path without symlinks.' >&2; exit 1; }
case "$data_home/" in "$bundle/"*) echo 'Data must be outside the release folder.' >&2; exit 1;; esac
[[ "$data_home" != *$'\n'* && "$data_home" != *$'\r'* ]] || { echo 'Newlines are not supported in data paths.' >&2; exit 1; }
pid_file="$data_home/native.pid"

require_installation() {
    [[ -f "$data_home/setup.complete" && -f "$data_home/installation.properties" ]] || { echo 'No completed Linux installation found.' >&2; return 1; }
    grep -qx 'mode=linux' "$data_home/installation.properties" || { echo 'This data directory is not a Linux native installation.' >&2; return 1; }
    for name in native.pid .native-control.lock logs runtime; do
        [[ ! -L "$data_home/$name" ]] || { echo 'Unexpected symlink in managed runtime paths.' >&2; return 1; }
    done
}

lock_control() {
    command -v flock >/dev/null || { echo 'flock (util-linux) is required.' >&2; exit 1; }
    exec 9>"$data_home/.native-control.lock"
    flock -x 9
}

process_ticks() {
    local stat
    [[ -r /proc/$1/stat ]] || return 1
    stat=$(cat "/proc/$1/stat") || return 1
    stat=${stat##*) }
    local fields
    read -ra fields <<< "$stat"
    [[ ${fields[0]} != Z ]] || return 1
    printf '%s' "${fields[19]}"
}

owned_process() {
    [[ -f "$pid_file" && ! -L "$pid_file" ]] || return 1
    local extra ticks boot arg found=0
    read -r native_pid ticks boot extra < "$pid_file" || return 1
    [[ $native_pid =~ ^[1-9][0-9]*$ && $ticks =~ ^[0-9]+$ && -z ${extra:-} ]] || return 1
    [[ $boot == "$(cat /proc/sys/kernel/random/boot_id)" ]] || return 1
    [[ $(process_ticks "$native_pid") == "$ticks" ]] || return 1
    [[ $(stat -c %u "/proc/$native_pid") == "$EUID" ]] || return 1
    while IFS= read -r -d '' arg; do
        [[ $arg == "-Dijob.native.home=$data_home" ]] && found=1
    done < "/proc/$native_pid/cmdline"
    [[ $found == 1 ]]
}

select_java() {
    java=${JAVA_HOME:+"$JAVA_HOME/bin/java"}
    java=${java:-$(command -v java || true)}
    [[ -n $java && -x $java ]] || { echo 'Install Java 21 or set JAVA_HOME to its directory.' >&2; exit 1; }
    local version
    version=$("$java" -version 2>&1 | awk -F '"' '/version "/ {print $2; exit}')
    [[ ${version%%.*} == 21 ]] || { echo 'Java 21 is required; check JAVA_HOME and PATH.' >&2; exit 1; }
}

file_uri() {
    local LC_ALL=C value=$1 char index
    printf 'file:'
    for ((index=0; index<${#value}; index++)); do
        char=${value:index:1}
        case "$char" in [a-zA-Z0-9/._~-]) printf '%s' "$char";; *) printf '%%%02X' "'$char";; esac
    done
}
