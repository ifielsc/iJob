// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.resource;

import com.ifielsc.ijob.contract.resource.SqlPreviewDTO;

public interface SqlPreview {
    SqlPreviewDTO.Result query(SqlPreviewDTO.Request request, String actor);

    void cancel(String requestId, String actor);
}
