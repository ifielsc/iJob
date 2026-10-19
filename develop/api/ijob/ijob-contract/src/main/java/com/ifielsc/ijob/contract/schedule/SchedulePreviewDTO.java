// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.schedule;

import lombok.Data;

import java.util.List;

@Data
public class SchedulePreviewDTO {
    private List<SchedulePreviewItemDTO> items;
}
