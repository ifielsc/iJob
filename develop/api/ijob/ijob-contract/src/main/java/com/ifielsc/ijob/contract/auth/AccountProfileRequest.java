// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountProfileRequest {
    private Long expectedRevision;
    private String displayName;
}
