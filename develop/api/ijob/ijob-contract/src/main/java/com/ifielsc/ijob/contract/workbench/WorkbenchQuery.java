package com.ifielsc.ijob.contract.workbench;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** UTC creation window, inclusive at both ends like the runtime list. */
@Getter
@Setter
public class WorkbenchQuery {
    private LocalDateTime from;
    private LocalDateTime to;
}
