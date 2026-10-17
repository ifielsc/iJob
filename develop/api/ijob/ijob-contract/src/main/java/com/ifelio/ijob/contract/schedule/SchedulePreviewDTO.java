package com.ifelio.ijob.contract.schedule;

import lombok.Data;

import java.util.List;

@Data
public class SchedulePreviewDTO {
    private List<SchedulePreviewItemDTO> items;
}
