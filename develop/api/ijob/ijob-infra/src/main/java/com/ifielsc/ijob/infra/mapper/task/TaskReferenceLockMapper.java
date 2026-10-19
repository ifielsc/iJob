// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.task;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskReferenceLockMapper {
    List<Long> lockExistingIds(@Param("ids") List<Long> ids);

    Long selectCurrentTaskDefinitionId(Long taskId);
}
