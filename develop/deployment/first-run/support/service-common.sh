#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

# Shared literal configuration reader; never source instance or service settings.
set -euo pipefail
umask 077
service_fail() { printf '%s\n' "$1" >&2; exit 1; }
service_path() {
    [[ $1 == /* && $1 != / && $1 != *$'\n'* && $1 != *$'\r'* ]] || service_fail 'Use an absolute non-root path.'
    [[ $(readlink -m -- "$1") == "$1" ]] || service_fail 'Use canonical paths without links or dot segments.'
    local part=$1
    while [[ $part != / ]]; do
        [[ ! -L $part ]] || service_fail 'Service paths must not contain symlinks.'
        part=$(dirname -- "$part")
    done
}
service_read() {
    local file=$1 allowed=$2 count=$3 destination=$4 line key value
    local -n values=$destination
    service_path "$file"
    [[ -f $file ]] || service_fail "Missing configuration: $file"
    values=()
    while IFS= read -r line || [[ -n $line ]]; do
        [[ -n $line && $line != \#* ]] || continue
        [[ $line == *=* && $line != *$'\r'* ]] || service_fail 'Invalid configuration line.'
        key=${line%%=*}; value=${line#*=}
        [[ $allowed == *"|$key|"* && -n $value && ! ${values[$key]+present} ]] || service_fail 'Unknown, duplicate or empty setting.'
        values[$key]=$value
    done < "$file"
    [[ ${#values[@]} == "$count" && ${values[version]:-} == 1 ]] || service_fail 'Incomplete configuration.'
}
declare -A instance=() service=()
service_separate() {
    [[ $1/ != "$2/"* && $2/ != "$1/"* ]] || service_fail 'Program, control and data directories must be separate.'
}
service_load_instance() {
    control=$1
    service_path "$control"
    service_read "$control/instance.conf" '|version|bundle|home|lang|mode|' 5 instance
    [[ ${instance[mode]} == linux && ( ${instance[lang]} == en || ${instance[lang]} == zh ) ]] || service_fail 'systemd requires a Linux native instance.'
    bundle=${instance[bundle]}; data_home=${instance[home]}
    service_path "$bundle"; service_path "$data_home"
    service_separate "$bundle" "$control"; service_separate "$bundle" "$data_home"; service_separate "$control" "$data_home"
}
service_load_registration() {
    service_read "$control/service.conf" '|version|name|user|java|control|bundle|home|unit_sha256|' 8 service
    [[ ${service[control]} == "$control" && ${service[bundle]} == "$bundle" && ${service[home]} == "$data_home" ]] || service_fail 'Service settings differ from instance.conf; restore settings or uninstall before upgrading.'
    [[ ${service[name]} =~ ^ijob-[a-z0-9][a-z0-9-]{0,39}$ && ${service[user]} =~ ^[a-z_][a-z0-9_-]*$ ]] || service_fail 'Invalid service name or account.'
    [[ ${service[unit_sha256]} =~ ^[a-f0-9]{64}$ ]] || service_fail 'Invalid service unit digest.'
    service_path "${service[java]}"
    service_path "$data_home/.service-owner"
    [[ -f $data_home/.service-owner && $(cat -- "$data_home/.service-owner") == "$control|${service[name]}" ]] || service_fail 'Service ownership marker does not match this control directory.'
}
service_say() { if [[ ${instance[lang]} == zh ]]; then printf '%s\n' "$2"; else printf '%s\n' "$1"; fi; }
