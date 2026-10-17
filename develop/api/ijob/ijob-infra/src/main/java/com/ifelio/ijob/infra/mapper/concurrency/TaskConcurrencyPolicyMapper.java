package com.ifelio.ijob.infra.mapper.concurrency;

import com.ifelio.ijob.infra.entity.concurrency.ConcurrencyResource;
import com.ifelio.ijob.infra.entity.concurrency.TaskConcurrencyPolicy;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskConcurrencyPolicyMapper {
    TaskConcurrencyPolicy selectByTaskId(@Param("taskId") Long taskId);

    int insert(TaskConcurrencyPolicy policy);

    int update(
            @Param("taskId") Long taskId,
            @Param("taskMaxConcurrency") Integer taskMaxConcurrency,
            @Param("expectedVersion") Long expectedVersion,
            @Param("updateTime") LocalDateTime updateTime);

    int deleteBindings(@Param("taskId") Long taskId);

    int insertBinding(
            @Param("taskId") Long taskId,
            @Param("resourceId") Long resourceId,
            @Param("createTime") LocalDateTime createTime);

    List<ConcurrencyResource> selectResourcesByTaskId(@Param("taskId") Long taskId);
}
