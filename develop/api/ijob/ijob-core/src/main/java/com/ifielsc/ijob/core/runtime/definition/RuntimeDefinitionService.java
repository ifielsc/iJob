// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.runtime.definition;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeDefinitionService {
    private final TaskDefinitionMapper taskDefinitionMapper;
    private final JobDefinitionMapper jobDefinitionMapper;

    public TaskAdmissionSnapshot loadTask(Long taskId, Long taskDefinitionId) {
        requirePositive(taskId, "Task ID");
        requirePositive(taskDefinitionId, "Task definition ID");
        TaskDefinition definition = taskDefinitionMapper.selectById(taskDefinitionId);
        validateTask(taskId, taskDefinitionId, definition);
        return new TaskAdmissionSnapshot(
                taskId, taskDefinitionId, definition.getRevision(), toExecutionSpec(definition));
    }

    public JobExecutionSpec loadJob(Long jobId, Long jobDefinitionId) {
        requirePositive(jobId, "Job ID");
        requirePositive(jobDefinitionId, "Job definition ID");
        JobDefinition definition = jobDefinitionMapper.selectById(jobDefinitionId);
        if (definition == null
                || !jobDefinitionId.equals(definition.getId())
                || !jobId.equals(definition.getJobId())
                || definition.getRevision() == null
                || definition.getRevision() <= 0
                || definition.getName() == null
                || definition.getName().isBlank()
                || definition.getTimeout() != null && definition.getTimeout() <= 0
                || !Boolean.TRUE.equals(definition.getEnabled())
                || definition.getCreateTime() == null) {
            throw systemError("Job runtime definition is invalid");
        }
        return new JobExecutionSpec(
                jobId,
                jobDefinitionId,
                definition.getRevision(),
                definition.getName(),
                definition.getTimeout());
    }

    private void validateTask(Long taskId, Long taskDefinitionId, TaskDefinition definition) {
        if (definition == null
                || !taskDefinitionId.equals(definition.getId())
                || !taskId.equals(definition.getTaskId())
                || definition.getRevision() == null
                || definition.getRevision() <= 0
                || definition.getName() == null
                || definition.getName().isBlank()
                || definition.getPluginType() == null
                || definition.getPluginConfig() == null
                || definition.getPluginConfig().isBlank()
                || definition.getRetryTimes() == null
                || definition.getRetryTimes() < 0
                || definition.getRetryInterval() == null
                || definition.getRetryInterval() < 0
                || definition.getTimeout() != null && definition.getTimeout() <= 0
                || !Boolean.TRUE.equals(definition.getEnabled())
                || definition.getCreateTime() == null) {
            throw systemError("Task runtime definition is invalid");
        }
    }

    private TaskExecutionSpec toExecutionSpec(TaskDefinition definition) {
        return new TaskExecutionSpec(
                definition.getName(),
                definition.getPluginType(),
                definition.getPluginConfig(),
                definition.getRetryTimes(),
                definition.getRetryInterval(),
                definition.getTimeout(),
                definition.getEnabled(),
                definition.getDescription());
    }

    private void requirePositive(Long id, String field) {
        if (id == null || id <= 0) {
            throw BusinessException.localized(ResultCode.CONFLICT, field + " snapshot is missing");
        }
    }

    private BusinessException systemError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }
}
