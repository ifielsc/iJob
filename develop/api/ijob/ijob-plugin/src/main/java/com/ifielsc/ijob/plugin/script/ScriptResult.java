// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScriptResult {
    private int exitCode;
    private String stdout;
    private String stderr;
}
