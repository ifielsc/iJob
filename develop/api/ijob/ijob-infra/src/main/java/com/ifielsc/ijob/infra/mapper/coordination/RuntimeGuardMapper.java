// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.coordination;

import org.apache.ibatis.annotations.Param;

public interface RuntimeGuardMapper {
    int lockAndAdvance(@Param("guardKey") String guardKey);

    long selectRevision(@Param("guardKey") String guardKey);
}
