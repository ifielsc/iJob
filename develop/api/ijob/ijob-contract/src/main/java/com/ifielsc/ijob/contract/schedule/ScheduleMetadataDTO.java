// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.schedule;

import com.ifielsc.ijob.contract.enums.ScheduleMisfirePolicy;

/** Public scheduling settings of the serving API node; not cluster liveness. */
public record ScheduleMetadataDTO(
        String zoneId,
        ScheduleMisfirePolicy defaultMisfirePolicy,
        long defaultMisfireGraceSec,
        int defaultMaxCatchupCount,
        long maxCatchupWindowSec,
        int maxPreviewCount,
        int maxIntervalSec,
        int maxCatchupCount,
        int maxVariableCount,
        int maxVariableBytes) {}
