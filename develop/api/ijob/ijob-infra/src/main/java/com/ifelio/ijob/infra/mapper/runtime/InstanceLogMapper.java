package com.ifelio.ijob.infra.mapper.runtime;

import com.ifelio.ijob.infra.entity.runtime.InstanceLog;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InstanceLogMapper {
    int insert(InstanceLog instanceLog);

    List<InstanceLog> selectLatest(
            @Param("instanceId") Long instanceId,
            @Param("runScope") boolean runScope,
            @Param("limit") int limit);

    List<InstanceLog> selectRunAfterId(
            @Param("rootId") Long rootId,
            @Param("afterId") Long afterId,
            @Param("limit") Integer limit);

    List<InstanceLog> selectAfterId(
            @Param("instanceId") Long instanceId,
            @Param("afterId") Long afterId,
            @Param("limit") Integer limit);
}
