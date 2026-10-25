#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/service-common.sh"
[[ $# -ge 2 ]] || service_fail 'Use ijob.sh service install|start|stop|restart|status|logs|enable|disable|uninstall.'
service_load_instance "$1"
action=$2; shift 2
case $action in install|start|stop|restart|status|logs|enable|disable|uninstall) ;; *) service_fail 'Unknown service action.';; esac
[[ $EUID == 0 ]] || service_fail 'Use sudo for service management; the application runs as its registered unprivileged account.'
[[ -d /run/systemd/system ]] || service_fail 'A systemd host is required.'
if [[ $action != logs ]]; then
    service_path "$control/.service-control.lock"
    exec 6>"$control/.service-control.lock"
    flock -n 6 || service_fail 'Another service operation is in progress.'
fi
unit_quote() {
    local value=$1
    value=${value//\\/\\\\}; value=${value//\"/\\\"}; value=${value//%/%%}
    if [[ ${2:-} == exec ]]; then value=${value//\$/\$\$}; fi
    printf '"%s"' "$value"
}
unit_path() { printf '%s' "${1//%/%%}"; }
if [[ $action == install ]]; then
    name= account= java_home=
    declare -A seen=()
    while [[ $# -gt 0 ]]; do
        [[ $# -ge 2 && ! ${seen[$1]+present} ]] || service_fail 'Missing or duplicate install option.'
        seen[$1]=1
        case $1 in --name) name=$2;; --user) account=$2;; --java-home) java_home=$2;; *) service_fail 'Use install --name ijob-<name> --user <existing-account> --java-home <Java-21-directory>.';; esac
        shift 2
    done
    [[ $name =~ ^ijob-[a-z0-9][a-z0-9-]{0,39}$ && $account =~ ^[a-z_][a-z0-9_-]*$ ]] || service_fail 'Specify an ijob- service name and an existing unprivileged account.'
    uid=$(id -u "$account"); group=$(id -gn "$account")
    [[ $uid != 0 ]] || service_fail 'The application must not run as root.'
    service_path "$java_home"
    java=$java_home/bin/java
    [[ -x $java && $("$java" -version 2>&1) == *'version "21.'* ]] || service_fail 'Java 21 is required.'
    [[ -d $data_home && -f $data_home/setup.complete && -f $data_home/application.properties && -d $data_home/runtime && -d $data_home/logs ]] || service_fail 'Initialize this instance and stop it before service installation.'
    grep -qx 'mode=linux' "$data_home/installation.properties" || service_fail 'Not a Linux native installation.'
    [[ $(stat -c %u "$data_home") == "$uid" ]] || service_fail 'Prepare the data home for the dedicated service account first; no ownership is changed implicitly.'
    for file in service.conf; do [[ ! -e $control/$file && ! -L $control/$file ]] || service_fail 'Service registration already exists; uninstall first.'; done
    [[ ! -e $data_home/.service-owner && ! -L $data_home/.service-owner ]] || service_fail 'Data already belongs to a service.'
    unit=/etc/systemd/system/$name.service
    [[ ! -e $unit && ! -L $unit && $(systemctl show "$name.service" -p LoadState --value) == not-found ]] || service_fail 'Service name is already in use.'
    for path in "$control" "$control/instance.conf" "$bundle" "$bundle/support/service-run.sh" "$bundle/support/service-common.sh"; do
        service_path "$path"
        [[ $(stat -c %u "$path") == 0 && -z $(find "$path" -maxdepth 0 -perm /022 -print) ]] || service_fail 'Program and control paths must be root-owned and not group/world writable.'
    done
    export IJOB_HOME="$data_home"
    source "$bundle/support/native-common.sh"
    require_installation
    lock_control
    chown "$account:$group" "$data_home/.native-control.lock"
    # Do not mistake an existing native process under another account for stopped.
    if [[ -f $pid_file ]]; then
        read -r candidate_pid candidate_ticks candidate_boot extra < "$pid_file" || service_fail 'Inspect malformed native PID record before installing.'
        if [[ $candidate_pid =~ ^[1-9][0-9]*$ && $candidate_boot == "$(cat /proc/sys/kernel/random/boot_id)" && $(process_ticks "$candidate_pid" || true) == "$candidate_ticks" ]]; then service_fail 'Stop the native instance before installing a service.'; fi
    fi
    service_path "$data_home/.native-runtime.lock"
    exec 7>"$data_home/.native-runtime.lock"
    flock -n 7 || service_fail 'Another process owns this instance runtime.'
    chown "$account:$group" "$data_home/.native-runtime.lock"
    work=$(mktemp -d "$control/.service-install-XXXXXXXX")
    trap '[[ $work == "$control"/.service-install-* ]] && rm -rf -- "$work"' EXIT
    {
        printf '[Unit]\nDescription=iJob instance %s\nAfter=network-online.target\nWants=network-online.target\nStartLimitIntervalSec=300\nStartLimitBurst=3\n\n[Service]\nType=exec\nUser=%s\nGroup=%s\n' "$name" "$account" "$group"
        printf 'WorkingDirectory=%s\nExecStart=/usr/bin/bash %s %s\n' "$(unit_path "$data_home/runtime")" "$(unit_quote "$bundle/support/service-run.sh" exec)" "$(unit_quote "$control" exec)"
        printf 'Restart=on-failure\nRestartSec=10\nKillSignal=SIGTERM\nKillMode=control-group\nTimeoutStopSec=120\nSendSIGKILL=no\nUMask=0077\nNoNewPrivileges=yes\nPrivateTmp=yes\nStandardOutput=journal\nStandardError=journal\n\n[Install]\nWantedBy=multi-user.target\n'
    } > "$work/$name.service"
    systemd-analyze verify "$work/$name.service"
    digest=$(sha256sum "$work/$name.service"); digest=${digest%% *}
    printf 'version=1\nname=%s\nuser=%s\njava=%s\ncontrol=%s\nbundle=%s\nhome=%s\nunit_sha256=%s\n' "$name" "$account" "$java" "$control" "$bundle" "$data_home" "$digest" > "$work/service.conf"
    # Keep a marker on partial failures: unmanaged entry points must fail closed.
    (set -C; printf '%s|%s\n' "$control" "$name" > "$data_home/.service-owner")
    chown "root:$group" "$data_home/.service-owner"; chmod 640 "$data_home/.service-owner"
    (set -C; cat "$work/service.conf" > "$control/service.conf")
    chown "root:$group" "$control" "$control/instance.conf" "$control/service.conf"
    chmod 750 "$control"; chmod 640 "$control/instance.conf" "$control/service.conf"
    (set -C; cat "$work/$name.service" > "$unit")
    chmod 644 "$unit"
    systemctl daemon-reload
    service_say "Installed $name, stopped, autostart disabled. Use service enable and start when ready." "已安装 $name，当前停止且未启用自启。准备好后使用 service enable 和 start。"
    exit 0
fi
once=false
if [[ $action == logs && $# == 1 && $1 == --once ]]; then once=true; shift; fi
[[ $# == 0 ]] || service_fail 'Unexpected service option.'
service_load_registration
name=${service[name]}; unit=/etc/systemd/system/$name.service
service_path "$unit"
[[ -f $unit && $(sha256sum "$unit" | cut -d ' ' -f 1) == "${service[unit_sha256]}" ]] || service_fail 'Registered systemd unit changed or is missing; inspect it before management.'
[[ $(systemctl show "$name.service" -p FragmentPath --value) == "$unit" ]] || service_fail 'systemd loaded a different service unit.'
case $action in
    start|restart)
        systemctl "$action" "$name.service"
        service_say 'Service process started; verify runtimePhase=READY in application logs.' '服务进程已启动；请在应用日志中确认 runtimePhase=READY。';;
    stop)
        systemctl stop "$name.service"
        [[ $(systemctl show "$name.service" -p MainPID --value) == 0 ]] || service_fail 'Shutdown is incomplete; data and process retained.';;
    status)
        systemctl show "$name.service" -p ActiveState -p SubState -p MainPID -p UnitFileState
        systemctl is-active --quiet "$name.service";;
    enable|disable) systemctl "$action" "$name.service";;
    logs)
        follow=(); $once || follow=(-f)
        journalctl -u "$name.service" -n 200 --no-pager "${follow[@]}";;
    uninstall)
        state=$(systemctl show "$name.service" -p ActiveState --value)
        [[ ( $state == inactive || $state == failed ) && $(systemctl show "$name.service" -p MainPID --value) == 0 ]] || service_fail 'Stop the service before uninstalling.'
        systemctl disable "$name.service"
        archive=$(mktemp -d "$control/service-uninstalled-XXXXXXXX")
        cp -- "$control/service.conf" "$unit" "$archive/"
        rm -- "$unit"
        systemctl daemon-reload
        rm -- "$data_home/.service-owner" "$control/service.conf"
        service_say "Uninstalled. Data, account and configuration retained. Registration backup: $archive" "已卸载。数据、账号和配置保留。服务登记备份：$archive";;
esac
