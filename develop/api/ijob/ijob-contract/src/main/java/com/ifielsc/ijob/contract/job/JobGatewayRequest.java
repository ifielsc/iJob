// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.job;

import com.ifielsc.ijob.contract.enums.GatewayMode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobGatewayRequest {
    private GatewayMode mode;
}
