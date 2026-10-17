package com.ifelio.ijob.infra.mapper.concurrency;

import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceDTO;
import com.ifelio.ijob.contract.concurrency.PluginActivityDTO;

import java.util.List;

public interface ConcurrencyContextMapper {
    List<ConcurrencyResourceDTO> selectResourceContexts(
            @org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    List<PluginActivityDTO> selectPluginActivity();
}
