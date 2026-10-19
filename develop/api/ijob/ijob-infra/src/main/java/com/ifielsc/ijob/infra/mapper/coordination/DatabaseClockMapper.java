package com.ifielsc.ijob.infra.mapper.coordination;

import java.time.LocalDateTime;

public interface DatabaseClockMapper {
    LocalDateTime currentTimestamp();
}
