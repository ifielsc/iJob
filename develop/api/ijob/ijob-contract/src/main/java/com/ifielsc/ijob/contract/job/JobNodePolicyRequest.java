// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.job;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobNodePolicyRequest {
    private FailStrategy failStrategy;
    private ExecuteCondition executeCondition;
}
