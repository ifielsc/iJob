// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.target;

import com.ifielsc.ijob.contract.enums.TargetType;

public interface TargetRunner {
    TargetType supportType();

    void run(Long instanceId);
}
