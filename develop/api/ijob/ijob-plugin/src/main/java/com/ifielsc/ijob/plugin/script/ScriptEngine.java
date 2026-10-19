package com.ifielsc.ijob.plugin.script;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScriptEngine {
    SHELL(".sh"),
    BAT(".bat"),
    POWERSHELL(".ps1"),
    PYTHON(".py");

    private final String suffix;
}
