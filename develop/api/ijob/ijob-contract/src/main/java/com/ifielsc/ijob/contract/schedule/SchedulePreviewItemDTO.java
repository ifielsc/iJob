package com.ifielsc.ijob.contract.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePreviewItemDTO {
    private LocalDateTime fireTime;
    private Boolean calendarAllowed;
}
