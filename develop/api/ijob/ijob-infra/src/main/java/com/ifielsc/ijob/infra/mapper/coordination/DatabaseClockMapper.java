// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.coordination;

import java.time.LocalDateTime;

public interface DatabaseClockMapper {
    LocalDateTime currentTimestamp();
}
