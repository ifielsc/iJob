// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.common;

import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteRequest {
    private List<Long> ids;
}
