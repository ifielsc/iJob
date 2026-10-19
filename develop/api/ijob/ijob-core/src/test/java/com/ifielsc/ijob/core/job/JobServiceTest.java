// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.job.JobDetailResponse;
import com.ifielsc.ijob.contract.job.JobListDTO;
import com.ifielsc.ijob.contract.job.JobPageQuery;
import com.ifielsc.ijob.contract.job.JobPublicationDTO;
import com.ifielsc.ijob.contract.job.JobUpsertRequest;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleDTO;
import com.ifielsc.ijob.core.job.definition.JobDefinitionPublicationService;
import com.ifielsc.ijob.core.job.definition.JobPublicationResult;
import com.ifielsc.ijob.core.job.retirement.JobRetirementResult;
import com.ifielsc.ijob.core.job.retirement.JobRetirementService;
import com.ifielsc.ijob.core.notify.NotifyBindingService;
import com.ifielsc.ijob.core.schedule.ScheduleService;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifielsc.ijob.infra.mapper.group.DefinitionGroupRow;
import com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionRow;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionGroupMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodePolicyMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class JobServiceTest {
    @Test
    void detailShouldReadExactCurrentDefinitionAndAggregateSummaries() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 1, 30);
        JobCurrentDefinitionRow identity = new JobCurrentDefinitionRow();
        identity.setId(8L);
        identity.setCurrentDefinitionId(31L);
        identity.setCurrentRevision(2L);
        identity.setRetired(false);
        identity.setCreateTime(now.minusDays(1));
        identity.setUpdateTime(now);
        JobDefinition definition = definition(now);
        JobDefinitionNode node = node(now);
        JobDefinitionNodePolicy policy = policy(now);
        RecordingScheduleService schedules = new RecordingScheduleService();
        RecordingBindingService bindings = new RecordingBindingService();
        JobService service =
                new JobService(
                        new CurrentMapper(identity),
                        new DefinitionMapper(definition),
                        groups(),
                        new NodeMapper(List.of(node)),
                        new PolicyMapper(List.of(policy)),
                        new EdgeMapper(List.of()),
                        schedules,
                        bindings,
                        publication(),
                        retirement());

        JobDetailResponse result = service.getDetail(8L);

        assertEquals(31L, result.getCurrentDefinitionId());
        assertEquals(2L, result.getCurrentRevision());
        assertEquals("current-job", result.getName());
        assertEquals("task-a", result.getNodes().getFirst().getNodeKey());
        assertEquals(100L, result.getNodes().getFirst().getId());
        assertEquals(List.of(), result.getEdges());
        assertSame(schedules.result, result.getSchedules());
        assertSame(bindings.result, result.getNotifyBindings());
        assertEquals(TargetType.JOB, schedules.targetType);
        assertEquals(8L, bindings.targetId);
    }

    @Test
    void createUpdateAndDeleteShouldDelegateVersionedServices() {
        RecordingPublication publications = new RecordingPublication();
        RecordingRetirement retirements = new RecordingRetirement();
        JobService service =
                new JobService(
                        new CurrentMapper(null),
                        new DefinitionMapper(null),
                        groups(),
                        new NodeMapper(List.of()),
                        new PolicyMapper(List.of()),
                        new EdgeMapper(List.of()),
                        new RecordingScheduleService(),
                        new RecordingBindingService(),
                        publications,
                        retirements);
        JobUpsertRequest request = new JobUpsertRequest();

        JobPublicationDTO created = service.create(request);
        JobPublicationDTO updated = service.update(8L, 4L, request);
        service.delete(8L);

        assertEquals(new JobPublicationDTO(8L, 31L, 1L), created);
        assertEquals(new JobPublicationDTO(8L, 32L, 5L), updated);
        assertSame(request, publications.createRequest);
        assertEquals(4L, publications.expectedRevision);
        assertEquals(8L, retirements.jobId);
    }

    @Test
    void pageShouldUseCurrentDefinitionMapper() {
        CurrentMapper mapper = new CurrentMapper(null);
        JobListDTO item = new JobListDTO();
        item.setId(8L);
        mapper.list = List.of(item);
        JobService service =
                new JobService(
                        mapper,
                        new DefinitionMapper(null),
                        groups(),
                        new NodeMapper(List.of()),
                        new PolicyMapper(List.of()),
                        new EdgeMapper(List.of()),
                        new RecordingScheduleService(),
                        new RecordingBindingService(),
                        publication(),
                        retirement());
        JobPageQuery query = new JobPageQuery();

        assertEquals(8L, service.queryPage(query).getList().getFirst().getId());
        assertSame(query, mapper.query);
    }

    @Test
    void batchDeleteShouldRetireInAscendingLockOrder() {
        RecordingRetirement retirements = new RecordingRetirement();
        JobService service =
                new JobService(
                        new CurrentMapper(null),
                        new DefinitionMapper(null),
                        groups(),
                        new NodeMapper(List.of()),
                        new PolicyMapper(List.of()),
                        new EdgeMapper(List.of()),
                        new RecordingScheduleService(),
                        new RecordingBindingService(),
                        publication(),
                        retirements);
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(9L, 3L, 5L));

        service.deleteBatch(request);

        assertEquals(List.of(3L, 5L, 9L), retirements.batchIds);
    }

    private static JobDefinition definition(LocalDateTime now) {
        JobDefinition definition = new JobDefinition();
        definition.setId(31L);
        definition.setJobId(8L);
        definition.setRevision(2L);
        definition.setName("current-job");
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setCreateTime(now.minusHours(1));
        return definition;
    }

    private static JobDefinitionNode node(LocalDateTime now) {
        JobDefinitionNode node = new JobDefinitionNode();
        node.setId(100L);
        node.setDefinitionId(31L);
        node.setNodeKey("task-a");
        node.setName("task-a");
        node.setNodeType(TargetType.TASK);
        node.setRefId(9L);
        node.setCreateTime(now);
        return node;
    }

    private static JobDefinitionNodePolicy policy(LocalDateTime now) {
        JobDefinitionNodePolicy policy = new JobDefinitionNodePolicy();
        policy.setId(200L);
        policy.setDefinitionId(31L);
        policy.setNodeId(100L);
        policy.setFailStrategy(FailStrategy.STOP);
        policy.setExecuteCondition(ExecuteCondition.ALL_SUCCESS);
        policy.setCreateTime(now);
        return policy;
    }

    private static JobDefinitionPublicationService publication() {
        return new RecordingPublication();
    }

    private static JobDefinitionGroupMapper groups() {
        return new JobDefinitionGroupMapper() {
            @Override
            public int insert(Long definitionId, String groupCode, LocalDateTime createTime) {
                return 1;
            }

            @Override
            public List<DefinitionGroupRow> selectByDefinitionIds(List<Long> definitionIds) {
                return List.of();
            }
        };
    }

    private static JobRetirementService retirement() {
        return new RecordingRetirement();
    }

    private static final class CurrentMapper implements JobCurrentDefinitionMapper {
        private final JobCurrentDefinitionRow row;
        private List<JobListDTO> list = List.of();
        private JobPageQuery query;

        private CurrentMapper(JobCurrentDefinitionRow row) {
            this.row = row;
        }

        @Override
        public List<JobListDTO> selectDtoList(JobPageQuery selected) {
            query = selected;
            return list;
        }

        @Override
        public JobCurrentDefinitionRow selectById(Long id) {
            return row;
        }
    }

    private record DefinitionMapper(JobDefinition selected) implements JobDefinitionMapper {
        @Override
        public JobDefinition selectById(Long id) {
            return selected;
        }

        @Override
        public List<JobDefinition> selectListByJobId(Long id) {
            throw unsupported();
        }

        @Override
        public int insert(JobDefinition ignored) {
            throw unsupported();
        }
    }

    private record NodeMapper(List<JobDefinitionNode> selected) implements JobDefinitionNodeMapper {
        @Override
        public List<JobDefinitionNode> selectListByDefinitionId(Long id) {
            return selected;
        }

        @Override
        public int insert(JobDefinitionNode ignored) {
            throw unsupported();
        }
    }

    private record PolicyMapper(List<JobDefinitionNodePolicy> selected)
            implements JobDefinitionNodePolicyMapper {
        @Override
        public List<JobDefinitionNodePolicy> selectListByDefinitionId(Long id) {
            return selected;
        }

        @Override
        public int insert(JobDefinitionNodePolicy ignored) {
            throw unsupported();
        }
    }

    private record EdgeMapper(List<JobDefinitionEdge> selected) implements JobDefinitionEdgeMapper {
        @Override
        public List<JobDefinitionEdge> selectListByDefinitionId(Long id) {
            return selected;
        }

        @Override
        public int insert(JobDefinitionEdge ignored) {
            throw unsupported();
        }
    }

    private static final class RecordingScheduleService extends ScheduleService {
        private final List<ScheduleDTO> result = List.of();
        private TargetType targetType;

        private RecordingScheduleService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<ScheduleDTO> listScheduleDetailsByTargetId(TargetType type, Long id) {
            targetType = type;
            return result;
        }
    }

    private static final class RecordingBindingService extends NotifyBindingService {
        private final List<NotifyBindingDTO> result = List.of();
        private Long targetId;

        private RecordingBindingService() {
            super(null, null, null, null);
        }

        @Override
        public List<NotifyBindingDTO> listDetailsByTarget(TargetType type, Long id) {
            targetId = id;
            return result;
        }
    }

    private static final class RecordingPublication extends JobDefinitionPublicationService {
        private JobUpsertRequest createRequest;
        private Long expectedRevision;

        private RecordingPublication() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public JobPublicationResult create(JobUpsertRequest request) {
            createRequest = request;
            return new JobPublicationResult(8L, 31L, 1L);
        }

        @Override
        public JobPublicationResult publish(Long jobId, Long expected, JobUpsertRequest request) {
            expectedRevision = expected;
            return new JobPublicationResult(jobId, 32L, expected + 1);
        }
    }

    private static final class RecordingRetirement extends JobRetirementService {
        private Long jobId;
        private final List<Long> batchIds = new ArrayList<>();

        private RecordingRetirement() {
            super(null, null, null, null);
        }

        @Override
        public JobRetirementResult retire(Long id) {
            jobId = id;
            return new JobRetirementResult(id, LocalDateTime.now(), false);
        }

        @Override
        public List<JobRetirementResult> retireBatch(List<Long> ids) {
            batchIds.addAll(ids);
            return ids.stream()
                    .map(id -> new JobRetirementResult(id, LocalDateTime.now(), false))
                    .toList();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used");
    }
}
