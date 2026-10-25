#!/usr/bin/env bash
# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

set -euo pipefail
umask 077
control=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
action=${1:-show}
[[ $# == 0 ]] || shift
fail() { printf '%s\n' "$1" >&2; exit 1; }
absolute_path() {
    [[ $1 == /* && $1 != / && $1 != *$'\n'* && $1 != *$'\r'* ]] || fail 'Use an absolute non-root path without newlines.'
    [[ $(readlink -m -- "$1") == "$1" ]] || fail 'Use canonical paths without links or dot segments.'
    local part=$1
    while [[ $part != / ]]; do
        [[ ! -L $part ]] || fail 'Instance paths must not contain symlinks.'
        part=$(dirname -- "$part")
    done
}
separate_paths() {
    [[ $1 != "$2" && $1/ != "$2/"* && $2/ != "$1/"* ]] || fail 'Program, control and data directories must be separate, non-nested directories.'
}
say() { if [[ $lang == zh ]]; then printf '%s\n' "$2"; else printf '%s\n' "$1"; fi; }
ask() {
    local label=$1 answer
    [[ $lang != zh ]] || label=$2
    printf '%s [%s] (! = cancel / 取消): ' "$label" "$3" >&2
    IFS= read -r answer || fail 'Configuration cancelled; existing settings retained.'
    [[ $answer != '!' ]] || fail 'Configuration cancelled; existing settings retained.'
    printf '%s' "${answer:-$3}"
}
declare -A settings=()
read_settings() {
    [[ -f $control/instance.conf && ! -L $control/instance.conf ]] || fail 'Run ijob.sh configure from a complete distribution first.'
    settings=()
    local line key value
    while IFS= read -r line || [[ -n $line ]]; do
        [[ -n $line && $line != \#* ]] || continue
        [[ $line != *$'\r'* && $line == *=* ]] || fail 'Invalid instance.conf line; use UTF-8 with LF newlines.'
        key=${line%%=*}; value=${line#*=}
        case $key in version|bundle|home|lang|mode) ;; *) fail 'Unknown instance.conf key.';; esac
        [[ -n $value && ! ${settings[$key]+present} ]] || fail 'Empty or duplicate instance.conf key.'
        settings[$key]=$value
    done < "$control/instance.conf"
    [[ ${#settings[@]} == 5 && ${settings[version]:-} == 1 ]] || fail 'Incomplete instance configuration.'
    [[ ${settings[lang]} == zh || ${settings[lang]} == en ]] || fail 'Use language zh or en.'
    [[ ${settings[mode]} == linux || ${settings[mode]} == docker ]] || fail 'Invalid instance mode.'
}
lock_instance() {
    absolute_path "$control/.instance-control.lock"
    command -v flock >/dev/null || fail 'flock (util-linux) is required.'
    exec 8>"$control/.instance-control.lock"
    flock -n 8 || fail 'Another command is managing this control directory; retry after it finishes.'
}
validate_bundle() {
    local name
    for name in ijob.sh ijob-setup-tool-1.0.0.jar ijob-starter-1.0.0.jar; do
        absolute_path "$bundle/$name"
        [[ -f $bundle/$name ]] || fail 'Use a complete distribution.'
    done
    if [[ $mode == docker ]]; then
        for name in docker-images.lock docker-images.compose.yaml; do
            absolute_path "$bundle/$name"
            [[ -f $bundle/$name ]] || fail 'Use a complete Docker distribution.'
        done
    else
        [[ -f $bundle/legacy/status.sh ]] || fail 'Use a complete native distribution with the current launcher layout.'
    fi
}
if [[ $action == configure ]]; then
    directory= data_home= lang=en mode=linux bundle=$control interactive=false update=false
    declare -A options=()
    while [[ $# -gt 0 ]]; do
        [[ ! ${options[$1]+present} ]] || fail 'Duplicate configure option.'
        options[$1]=1
        case $1 in
            --interactive) interactive=true; shift; continue;;
            --update) update=true; shift; continue;;
        esac
        [[ $# -ge 2 ]] || fail 'Each configure option requires a value.'
        case $1 in
            --directory) directory=$2;;
            --home) data_home=$2;;
            --lang) lang=$2;;
            --mode) mode=$2;;
            --bundle) bundle=$2;;
            *) fail 'Use configure [--interactive] --directory <new-control-directory> --home <data-directory> [--lang zh|en] [--mode linux|docker], or configure --update [--bundle <program-directory>] [--lang zh|en].';;
        esac
        shift 2
    done
    if ! $interactive && ! $update && [[ -z $directory && -z $data_home ]]; then
        [[ -t 0 ]] || fail 'Use explicit configure parameters or --interactive.'
        interactive=true
    fi
    if $update; then
        [[ ! ${options[--directory]+present} && ! ${options[--home]+present} && ! ${options[--mode]+present} ]] || fail 'Home, control directory and mode are fixed; create a new control directory for an explicit migration.'
        read_settings
        lock_instance
        read_settings
        directory=$control; data_home=${settings[home]}; mode=${settings[mode]}
        [[ ! -e $control/service.conf && ! -e $data_home/.service-owner ]] || fail 'Stop and uninstall service management before updating settings; data is retained.'
        [[ ${options[--lang]+present} ]] || lang=${settings[lang]}
        [[ ${options[--bundle]+present} ]] || bundle=${settings[bundle]}
    else
        [[ ! ${options[--bundle]+present} ]] || fail '--bundle is only supported by configure --update.'
    fi
    if $interactive; then
        lang=$(ask 'Prompt language: en or zh' '提示语言：en 或 zh' "$lang")
        [[ $lang == en || $lang == zh ]] || fail 'Use language en or zh.'
        say 'Only launch settings are saved. Accounts/database settings are configured on first start.' '这里只保存启动设置；首次启动时另行设置应用账号和数据库。'
        if $update; then
            bundle=$(ask 'Program directory' '程序目录' "$bundle")
        else
            directory=$(ask 'New control directory' '新控制目录' "${directory:-$(dirname -- "$control")/ijob-instance}")
            data_home=$(ask 'Data directory (existing data is retained)' '数据目录（保留已有数据）' "${data_home:-$(dirname -- "$control")/ijob-data}")
            mode=$(ask 'Mode: linux or docker' '运行模式：linux 或 docker' "$mode")
        fi
    fi
    [[ $lang == en || $lang == zh ]] || fail 'Use language en or zh.'
    [[ $mode == linux || $mode == docker ]] || fail 'Use mode linux or docker.'
    absolute_path "$directory"; absolute_path "$data_home"; absolute_path "$bundle"
    separate_paths "$directory" "$data_home"; separate_paths "$bundle" "$data_home"; separate_paths "$bundle" "$directory"
    if ! $update; then
        [[ ! -e $directory && ! -L $directory ]] || fail 'Control directory already exists; existing configuration retained.'
    fi
    [[ -d $(dirname -- "$directory") && -d $(dirname -- "$data_home") ]] || fail 'Create the control and data parent directories first.'
    [[ ! -e $data_home || -d $data_home ]] || fail 'Data home must be a directory.'
    validate_bundle
    if $interactive; then
        printf 'bundle=%s\nhome=%s\ncontrol=%s\nlang=%s\nmode=%s\n' "$bundle" "$data_home" "$directory" "$lang" "$mode"
        [[ $(ask 'Save these settings? Type yes' '保存以上设置？输入 yes' 'no') == yes ]] || fail 'Configuration cancelled; existing settings retained.'
    fi
    if $update; then
        if [[ -e $data_home ]]; then
            export IJOB_HOME="$data_home" IJOB_LANG="${settings[lang]}"
            old_bundle=${settings[bundle]}
            absolute_path "$old_bundle"
            separate_paths "$old_bundle" "$control"; separate_paths "$old_bundle" "$data_home"
            if [[ $mode == docker ]]; then
                [[ -f $data_home/compose.yaml ]] || fail 'No initialized Docker installation; inspect the data directory before updating.'
                running=$(docker compose -f "$data_home/compose.yaml" ps --status running --quiet)
                [[ -z $running ]] || fail 'Stop the instance before changing launch settings.'
            else
                entry_dir=$old_bundle/legacy
                [[ -d $entry_dir ]] || entry_dir=$old_bundle
                result=0
                bash "$entry_dir/status.sh" || result=$?
                [[ $result == 3 ]] || fail 'Stop the instance and verify STOPPED before changing launch settings.'
            fi
        fi
        backup=$(mktemp "$control/instance.conf.backup-XXXXXXXX")
        cp -- "$control/instance.conf" "$backup"
        temporary=$(mktemp "$control/instance.conf.new-XXXXXXXX")
        trap 'rm -f -- "$temporary"' EXIT
        printf 'version=1\nbundle=%s\nhome=%s\nlang=%s\nmode=%s\n' "$bundle" "$data_home" "$lang" "$mode" > "$temporary"
        mv -fT -- "$temporary" "$control/instance.conf"
        say "Updated. Previous settings: $backup" "已更新。原设置备份：$backup"
        exit 0
    fi
    staging=$(mktemp -d "$directory.new-XXXXXXXX")
    trap '[[ $staging == "$directory".new-* ]] && rm -rf -- "$staging"' EXIT
    printf 'version=1\nbundle=%s\nhome=%s\nlang=%s\nmode=%s\n' "$bundle" "$data_home" "$lang" "$mode" > "$staging/instance.conf"
    cp -- "$bundle/ijob.sh" "$staging/ijob.sh"
    chmod 700 -- "$staging/ijob.sh"
    mv -nT -- "$staging" "$directory"
    [[ ! -e $staging ]] || fail 'Control directory appeared during configuration; existing files retained.'
    say "Configured. Run $directory/ijob.sh start. Application data was not initialized." "配置完成。运行 $directory/ijob.sh start。尚未初始化应用数据。"
    exit 0
fi
case $action in start|stop|restart|status|logs|show|service) ;; *) fail 'Use configure, start, stop, restart, status, logs, show or service.';; esac
service_args=()
if [[ $action == service ]]; then service_args=("$@"); set --; fi
once=false
if [[ $action == logs && $# == 1 && $1 == --once ]]; then once=true; shift; fi
[[ $# == 0 ]] || fail 'Runtime commands read instance.conf; only logs accepts --once.'
[[ -f $control/instance.conf && ! -L $control/instance.conf ]] || fail 'Run ijob.sh configure from a complete distribution first.'
if [[ $action != logs && ! ( $action == service && ${service_args[0]:-} == logs ) ]]; then lock_instance; fi
read_settings
bundle=${settings[bundle]}; data_home=${settings[home]}; lang=${settings[lang]}; mode=${settings[mode]}
[[ $lang == zh || $lang == en ]] || fail 'Use language zh or en.'
[[ $mode == linux || $mode == docker ]] || fail 'Invalid instance mode.'
absolute_path "$control"; absolute_path "$bundle"; absolute_path "$data_home"
separate_paths "$control" "$bundle"; separate_paths "$control" "$data_home"; separate_paths "$bundle" "$data_home"
if [[ $action == show ]]; then printf 'bundle=%s\nhome=%s\nlang=%s\nmode=%s\n' "$bundle" "$data_home" "$lang" "$mode"; exit 0; fi
if [[ $action == service || -e $control/service.conf || -e $data_home/.service-owner ]]; then
    [[ $mode == linux ]] || fail 'Native systemd management is only supported in linux mode; Docker uses Compose restart policy.'
    helper=$bundle/support/service.sh
    [[ -f $helper && ! -L $helper ]] || fail 'Service management helper is missing; use the complete current bundle.'
    if [[ $action == service ]]; then
        bash "$helper" "$control" "${service_args[@]}" 8>&-
    else
        extra=(); $once && extra=(--once)
        bash "$helper" "$control" "$action" "${extra[@]}" 8>&-
    fi
    exit $?
fi
export IJOB_HOME="$data_home" IJOB_LANG="$lang"
say "Instance home: $data_home" "实例数据目录：$data_home"
entry_dir=$bundle/legacy
[[ -d $entry_dir ]] || entry_dir=$bundle
if [[ $mode == linux ]]; then
    case $action in
        restart) bash "$entry_dir/stop.sh" 8>&-; bash "$entry_dir/start.sh" 8>&-;;
        logs)
            if $once; then
                absolute_path "$data_home/logs/ijob.log"
                exec tail -n 200 -- "$data_home/logs/ijob.log"
            fi
            exec bash "$entry_dir/logs.sh";;
        *) bash "$entry_dir/$action.sh" 8>&-;;
    esac
else
    case $action in
        start) exec bash "$entry_dir/start-docker.sh";;
        restart) exec bash "$entry_dir/start-docker.sh" --recreate;;
        stop) exec bash "$entry_dir/stop-docker.sh";;
        status) exec docker compose -f "$data_home/compose.yaml" ps;;
        logs)
            follow=(); $once || follow=(-f)
            exec docker compose -f "$data_home/compose.yaml" logs --tail 200 "${follow[@]}" backend web;;
    esac
fi
