// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.runtime.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class RuntimeDefinitionServiceTest {
    @Test
    void shouldLoadExactTaskSnapshotWithoutCurrentPointerRead() {
        RuntimeDefinitionService service = service(taskDefinition(), jobDefinition());

        TaskAdmissionSnapshot snapshot = service.loadTask(9L, 41L);

        assertEquals(9L, snapshot.taskId());
        assertEquals(41L, snapshot.taskDefinitionId());
        assertEquals(3L, snapshot.revision());
        assertEquals("secret", snapshot.executionSpec().getPluginConfig());
        assertFalse(snapshot.executionSpec().toString().contains("secret"));
    }

    @Test
    void shouldLoadExactJobExecutionSpec() {
        RuntimeDefinitionService service = service(taskDefinition(), jobDefinition());

        JobExecutionSpec spec = service.loadJob(8L, 31L);

        assertEquals(8L, spec.jobId());
        assertEquals(31L, spec.jobDefinitionId());
        assertEquals(2L, spec.revision());
        assertEquals("job", spec.name());
    }

    @Test
    void shouldRejectMissingOrMismatchedSnapshotsWithoutFallback() {
        RuntimeDefinitionService service = service(taskDefinition(), jobDefinition());
        BusinessException missing =
                assertThrows(BusinessException.class, () -> service.loadTask(9L, null));
        BusinessException mismatch =
                assertThrows(BusinessException.class, () -> service.loadJob(9L, 31L));

        assertEquals(ResultCode.CONFLICT, missing.getCode());
        assertEquals(ResultCode.SYSTEM_ERROR, mismatch.getCode());
    }

    private static RuntimeDefinitionService service(TaskDefinition task, JobDefinition job) {
        return new RuntimeDefinitionService(
                new FakeTaskDefinitionMapper(task), new FakeJobDefinitionMapper(job));
    }

    private static TaskDefinition taskDefinition() {
        TaskDefinition definition = new TaskDefinition();
        definition.setId(41L);
        definition.setTaskId(9L);
        definition.setRevision(3L);
        definition.setName("task");
        definition.setPluginType(PluginType.SCRIPT);
        definition.setPluginConfig("secret");
        definition.setRetryTimes(1);
        definition.setRetryInterval(2);
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setCreateTime(LocalDateTime.now());
        return definition;
    }

    private static JobDefinition jobDefinition() {
        JobDefinition definition = new JobDefinition();
        definition.setId(31L);
        definition.setJobId(8L);
        definition.setRevision(2L);
        definition.setName("job");
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setCreateTime(LocalDateTime.now());
        return definition;
    }

    private record FakeTaskDefinitionMapper(TaskDefinition definition)
            implements TaskDefinitionMapper {
        @Override
        public TaskDefinition selectById(Long id) {
            return definition;
        }

        @Override
        public List<TaskDefinition> selectListByTaskId(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(TaskDefinition ignored) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeJobDefinitionMapper(JobDefinition definition)
            implements JobDefinitionMapper {
        @Override
        public JobDefinition selectById(Long id) {
            return definition;
        }

        @Override
        public List<JobDefinition> selectListByJobId(Long jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(JobDefinition ignored) {
            throw new UnsupportedOperationException();
        }
    }
}
