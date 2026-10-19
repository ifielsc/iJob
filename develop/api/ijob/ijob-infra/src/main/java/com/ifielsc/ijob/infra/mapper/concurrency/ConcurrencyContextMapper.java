// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.concurrency;

import com.ifielsc.ijob.contract.concurrency.ConcurrencyResourceDTO;
import com.ifielsc.ijob.contract.concurrency.PluginActivityDTO;

import java.util.List;

public interface ConcurrencyContextMapper {
    List<ConcurrencyResourceDTO> selectResourceContexts(
            @org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    List<PluginActivityDTO> selectPluginActivity();
}
