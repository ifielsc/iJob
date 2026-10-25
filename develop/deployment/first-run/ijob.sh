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
if [[ $action == configure ]]; then
    directory= data_home= lang=en mode=linux
    declare -A options=()
    while [[ $# -gt 0 ]]; do
        [[ $# -ge 2 ]] || fail 'Each configure option requires a value.'
        case $1 in
            --directory) directory=$2;;
            --home) data_home=$2;;
            --lang) lang=$2;;
            --mode) mode=$2;;
            *) fail 'Use configure --directory <new-control-directory> --home <data-directory> [--lang zh|en] [--mode linux|docker].';;
        esac
        [[ ! ${options[$1]+present} ]] || fail 'Duplicate configure option.'
        options[$1]=1
        shift 2
    done
    [[ $lang == en || $lang == zh ]] || fail 'Use language en or zh.'
    [[ $mode == linux || $mode == docker ]] || fail 'Use mode linux or docker.'
    absolute_path "$directory"; absolute_path "$data_home"; absolute_path "$control"
    separate_paths "$directory" "$data_home"; separate_paths "$control" "$data_home"; separate_paths "$control" "$directory"
    [[ ! -e $directory && ! -L $directory ]] || fail 'Control directory already exists; existing configuration retained.'
    [[ -d $(dirname -- "$directory") && -d $(dirname -- "$data_home") ]] || fail 'Create the control and data parent directories first.'
    [[ -f $control/ijob-setup-tool-1.0.0.jar && -f $control/ijob-starter-1.0.0.jar ]] || fail 'Configure from a complete distribution.'
    if [[ $mode == docker ]]; then
        [[ -f $control/docker-images.lock && -f $control/docker-images.compose.yaml ]] || fail 'Use a complete Docker distribution.'
    fi
    mkdir -- "$directory"
    printf 'version=1\nbundle=%s\nhome=%s\nlang=%s\nmode=%s\n' "$control" "$data_home" "$lang" "$mode" > "$directory/instance.conf"
    cp -- "$control/ijob.sh" "$directory/ijob.sh"
    chmod 700 -- "$directory/ijob.sh"
    say "Configured. Run $directory/ijob.sh start. Application data was not initialized." "配置完成。运行 $directory/ijob.sh start。尚未初始化应用数据。"
    exit 0
fi
case $action in start|stop|restart|status|logs|show) ;; *) fail 'Use configure, start, stop, restart, status, logs or show.';; esac
once=false
if [[ $action == logs && $# == 1 && $1 == --once ]]; then once=true; shift; fi
[[ $# == 0 ]] || fail 'Runtime commands read instance.conf; only logs accepts --once.'
[[ -f $control/instance.conf && ! -L $control/instance.conf ]] || fail 'Run ijob.sh configure from a complete distribution first.'
declare -A settings=()
while IFS= read -r line || [[ -n $line ]]; do
    [[ -n $line && $line != \#* ]] || continue
    [[ $line != *$'\r'* && $line == *=* ]] || fail 'Invalid instance.conf line; use UTF-8 with LF newlines.'
    key=${line%%=*}; value=${line#*=}
    case $key in version|bundle|home|lang|mode) ;; *) fail 'Unknown instance.conf key.';; esac
    [[ -n $value && ! ${settings[$key]+present} ]] || fail 'Empty or duplicate instance.conf key.'
    settings[$key]=$value
done < "$control/instance.conf"
[[ ${#settings[@]} == 5 && ${settings[version]:-} == 1 ]] || fail 'Incomplete instance configuration.'
bundle=${settings[bundle]}; data_home=${settings[home]}; lang=${settings[lang]}; mode=${settings[mode]}
[[ $lang == zh || $lang == en ]] || fail 'Use language zh or en.'
[[ $mode == linux || $mode == docker ]] || fail 'Invalid instance mode.'
absolute_path "$control"; absolute_path "$bundle"; absolute_path "$data_home"
separate_paths "$control" "$bundle"; separate_paths "$control" "$data_home"; separate_paths "$bundle" "$data_home"
if [[ $action == show ]]; then printf 'bundle=%s\nhome=%s\nlang=%s\nmode=%s\n' "$bundle" "$data_home" "$lang" "$mode"; exit 0; fi
export IJOB_HOME="$data_home" IJOB_LANG="$lang"
say "Instance home: $data_home" "实例数据目录：$data_home"
if [[ $mode == linux ]]; then
    case $action in
        restart) bash "$bundle/stop.sh"; exec bash "$bundle/start.sh";;
        logs)
            if $once; then
                absolute_path "$data_home/logs/ijob.log"
                exec tail -n 200 -- "$data_home/logs/ijob.log"
            fi
            exec bash "$bundle/logs.sh";;
        *) exec bash "$bundle/$action.sh";;
    esac
else
    case $action in
        start) exec bash "$bundle/start-docker.sh";;
        restart) exec bash "$bundle/start-docker.sh" --recreate;;
        stop) exec bash "$bundle/stop-docker.sh";;
        status) exec docker compose -f "$data_home/compose.yaml" ps;;
        logs)
            follow=(); $once || follow=(-f)
            exec docker compose -f "$data_home/compose.yaml" logs --tail 200 "${follow[@]}" backend web;;
    esac
fi
