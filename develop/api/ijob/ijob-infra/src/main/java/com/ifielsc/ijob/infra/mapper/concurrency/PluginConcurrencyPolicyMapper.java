// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.concurrency;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.infra.entity.concurrency.PluginConcurrencyPolicy;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginConcurrencyPolicyMapper {
    List<PluginConcurrencyPolicy> selectAll();

    PluginConcurrencyPolicy selectByPluginType(@Param("pluginType") PluginType pluginType);

    PluginConcurrencyPolicy selectByPluginTypeForUpdate(@Param("pluginType") PluginType pluginType);

    int insert(PluginConcurrencyPolicy policy);

    int update(
            @Param("pluginType") PluginType pluginType,
            @Param("maxConcurrency") Integer maxConcurrency,
            @Param("expectedVersion") Long expectedVersion,
            @Param("newVersion") Long newVersion,
            @Param("updateTime") java.time.LocalDateTime updateTime);

    int delete(@Param("pluginType") PluginType pluginType, @Param("version") Long version);
}
