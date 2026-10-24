// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.etl;

import com.ifielsc.ijob.contract.task.EtlInspectionDTO;

public interface EtlInspection {
    EtlInspectionDTO.Progress progress(Long taskId);

    EtlInspectionDTO.Tables tables(Long resourceId, String schema, boolean includeViews);

    EtlInspectionDTO.Checks check(String pluginConfig);
}
