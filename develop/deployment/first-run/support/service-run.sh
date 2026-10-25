#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/service-common.sh"
[[ $# == 1 ]] || service_fail 'Internal service runner requires one control directory.'
service_load_instance "$1"
service_load_registration
[[ $(id -un) == "${service[user]}" ]] || service_fail 'Service account does not match registration.'
[[ -f $data_home/setup.complete && -f $data_home/application.properties ]] || service_fail 'Initialize this instance interactively before installing a service.'
grep -qx 'mode=linux' "$data_home/installation.properties" || service_fail 'Not a Linux native installation.'
vendor=$(sed -n 's/^vendor=//p' "$data_home/installation.properties")
case $vendor in sqlite|mysql|postgresql|sqlserver|oracle) ;; *) service_fail 'Invalid database metadata.';; esac
java=${service[java]}
version=$("$java" -version 2>&1)
[[ $version == *'version "21.'* ]] || service_fail 'Java 21 is required.'
for name in .native-runtime.lock runtime logs application.properties installation.properties setup.complete; do service_path "$data_home/$name"; done
exec 7>"$data_home/.native-runtime.lock"
flock -n 7 || service_fail 'Another process owns this instance runtime.'
cd -- "$data_home/runtime"
# Reuse percent-encoded file URIs without invoking setup or a background launcher.
export IJOB_HOME="$data_home"
source "$bundle/support/native-common.sh"
session="service-$(date +%s)-$$"
exec env -i PATH=/usr/bin:/bin LANG=C.UTF-8 HOME="$HOME" IJOB_HOME="$data_home" IJOB_LANG="${instance[lang]}" IJOB_PLATFORM_DB="$vendor" \
    "$java" "-Dijob.native.home=$data_home" -jar "$bundle/ijob-starter-1.0.0.jar" \
    --spring.profiles.active=release,native-background \
    "--spring.config.additional-location=$(file_uri "$data_home/application.properties")" \
    "--app.web.directory=$bundle/web-dist" "--logging.config=$(file_uri "$bundle/support/logging-native.xml")" \
    "--ijob.native.log-directory=$data_home/logs" "--ijob.native.log-session=$session"
