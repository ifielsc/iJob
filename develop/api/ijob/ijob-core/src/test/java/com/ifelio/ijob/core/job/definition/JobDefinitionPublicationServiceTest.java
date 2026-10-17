package com.ifelio.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.job.JobEdgeRequest;
import com.ifelio.ijob.contract.job.JobNodePolicyRequest;
import com.ifelio.ijob.contract.job.JobNodeRequest;
import com.ifelio.ijob.contract.job.JobUpsertRequest;
import com.ifelio.ijob.core.execute.graph.JobGraphDefinitionValidator;
import com.ifelio.ijob.infra.entity.job.Job;
import com.ifelio.ijob.infra.entity.job.JobDefinition;
import com.ifelio.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifelio.ijob.infra.entity.job.JobDefinitionNode;
import com.ifelio.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifelio.ijob.infra.mapper.group.DefinitionGroupRow;
import com.ifelio.ijob.infra.mapper.job.JobCurrentDefinitionAdvance;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionGroupMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionNodePolicyMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionReferenceSnapshotMapper;
import com.ifelio.ijob.infra.mapper.job.JobIdentityRetirement;
import com.ifelio.ijob.infra.mapper.job.JobPublicationIdentityRow;
import com.ifelio.ijob.infra.mapper.job.JobPublicationMapper;
import com.ifelio.ijob.infra.mapper.job.JobReferenceRow;
import com.ifelio.ijob.infra.mapper.task.TaskReferenceLockMapper;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class JobDefinitionPublicationServiceTest {
    @Test
    void createShouldLockValidateWriteCompleteRevisionAndInitialize() {
        Harness harness = new Harness();
        harness.publication.lockedJobIds = List.of(20L);
        harness.snapshots.currentJobIds = List.of(20L);
        JobUpsertRequest request =
                request(
                        List.of(
                                node("task-b", TargetType.TASK, 9L),
                                node("task-a", TargetType.TASK, 3L),
                                node("nested", TargetType.JOB, 20L)),
                        List.of(edge("task-b", "task-a"), edge("task-a", "nested")));

        JobPublicationResult result = harness.service.create(request);

        assertEquals(100L, result.jobId());
        assertEquals(1L, result.revision());
        assertEquals(List.of(3L, 9L), harness.tasks.requestedIds);
        assertEquals(
                List.of(
                        "barrier",
                        "task-lock",
                        "snapshot-ids",
                        "snapshot-references",
                        "identity-insert",
                        "definition-insert:1",
                        "node-insert:task-b",
                        "policy-insert:201",
                        "node-insert:task-a",
                        "policy-insert:202",
                        "node-insert:nested",
                        "policy-insert:203",
                        "edge-insert:201->202",
                        "edge-insert:202->203",
                        "pointer-initialize"),
                harness.order);
        assertEquals(1L, harness.definitions.inserted.getRevision());
        assertEquals(100L, harness.definitions.inserted.getJobId());
        assertEquals(Boolean.TRUE, harness.identities.inserted.getEnabled());
        assertEquals(harness.definitions.inserted.getId(), harness.publication.initialDefinitionId);
    }

    @Test
    void createShouldPersistCanonicalGroupsOnThePublishedDefinition() {
        Harness harness = new Harness();
        JobUpsertRequest request = request(List.of(node("task", TargetType.TASK, 3L)), null);
        request.setGroups(List.of(" GPU ", "cpu", "gpu"));

        harness.service.create(request);

        assertEquals(List.of("200:cpu", "200:gpu"), harness.groups.inserted);
        assertEquals(List.of("cpu", "gpu"), request.getGroups());
    }

    @Test
    void publishShouldLockCurrentDefinitionAndAdvanceNextRevision() {
        Harness harness = new Harness();
        harness.publication.lockedJobIds = List.of(10L, 20L);
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);
        harness.snapshots.currentJobIds = List.of(10L, 20L);
        JobUpsertRequest request = request(List.of(node("nested", TargetType.JOB, 20L)), null);
        request.setEnabled(null);

        JobPublicationResult result = harness.service.publish(10L, 4L, request);

        assertEquals(10L, result.jobId());
        assertEquals(5L, result.revision());
        assertTrue(harness.tasks.requestedIds.isEmpty());
        assertEquals(
                List.of(
                        "barrier",
                        "identity-lock",
                        "definition-select",
                        "snapshot-ids",
                        "snapshot-references",
                        "definition-insert:5",
                        "node-insert:nested",
                        "policy-insert:201",
                        "pointer-advance"),
                harness.order);
        JobCurrentDefinitionAdvance advance = harness.publication.advance;
        assertEquals(10L, advance.getJobId());
        assertEquals(1000L, advance.getExpectedDefinitionId());
        assertEquals(4L, advance.getExpectedRevision());
        assertEquals(5L, advance.getNextRevision());
        assertEquals(harness.definitions.inserted.getId(), advance.getDefinitionId());
        assertTrue(advance.getEnabled());
    }

    @Test
    void invalidRequestAndExpectedRevisionShouldFailBeforeBarrier() {
        Harness harness = new Harness();

        BusinessException invalidRequest =
                assertThrows(BusinessException.class, () -> harness.service.create(null));
        assertEquals(ResultCode.PARAM_ERROR, invalidRequest.getCode());
        assertTrue(harness.order.isEmpty());

        BusinessException invalidRevision =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.publish(
                                        10L,
                                        0L,
                                        request(List.of(node("task", TargetType.TASK, 1L)), null)));
        assertEquals(ResultCode.PARAM_ERROR, invalidRevision.getCode());
        assertTrue(harness.order.isEmpty());
    }

    @Test
    void missingLockedTaskShouldStopBeforeSnapshotAndWrites() {
        Harness harness = new Harness();
        harness.tasks.existingIds = Set.of(3L);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.create(
                                        request(
                                                List.of(
                                                        node("task-a", TargetType.TASK, 3L),
                                                        node("task-b", TargetType.TASK, 9L)),
                                                List.of(edge("task-a", "task-b")))));

        assertEquals(ResultCode.NOT_FOUND, exception.getCode());
        assertEquals(List.of("barrier", "task-lock"), harness.order);
    }

    @Test
    void jobReferenceMustExistAndHaveCurrentDefinition() {
        Harness missing = new Harness();
        missing.publication.lockedJobIds = List.of();

        BusinessException notFound =
                assertThrows(
                        BusinessException.class,
                        () ->
                                missing.service.create(
                                        request(
                                                List.of(node("nested", TargetType.JOB, 20L)),
                                                null)));
        assertEquals(ResultCode.NOT_FOUND, notFound.getCode());
        assertEquals(List.of("barrier", "snapshot-ids", "snapshot-references"), missing.order);

        Harness unpublished = new Harness();
        unpublished.publication.lockedJobIds = List.of(20L);
        BusinessException conflict =
                assertThrows(
                        BusinessException.class,
                        () ->
                                unpublished.service.create(
                                        request(
                                                List.of(node("nested", TargetType.JOB, 20L)),
                                                null)));
        assertEquals(ResultCode.CONFLICT, conflict.getCode());
        assertEquals(List.of("barrier", "snapshot-ids", "snapshot-references"), unpublished.order);
    }

    @Test
    void publishShouldClassifyIdentityAdmissionFailures() {
        JobUpsertRequest request = request(List.of(node("task", TargetType.TASK, 3L)), null);

        Harness missing = new Harness();
        BusinessException notFound =
                assertThrows(
                        BusinessException.class, () -> missing.service.publish(10L, 4L, request));
        assertEquals(ResultCode.NOT_FOUND, notFound.getCode());

        Harness legacy = new Harness();
        legacy.publication.identity = identity(10L, null, null);
        BusinessException legacyConflict =
                assertThrows(
                        BusinessException.class, () -> legacy.service.publish(10L, 4L, request));
        assertEquals(ResultCode.CONFLICT, legacyConflict.getCode());

        Harness retired = new Harness();
        retired.publication.identity = identity(10L, 1000L, 4L);
        retired.publication.identity.setRetired(true);
        retired.publication.identity.setRetiredTime(LocalDateTime.now());
        BusinessException retiredConflict =
                assertThrows(
                        BusinessException.class, () -> retired.service.publish(10L, 4L, request));
        assertEquals(ResultCode.CONFLICT, retiredConflict.getCode());

        Harness broken = new Harness();
        broken.publication.identity = identity(10L, 1000L, null);
        BusinessException systemError =
                assertThrows(
                        BusinessException.class, () -> broken.service.publish(10L, 4L, request));
        assertEquals(ResultCode.SYSTEM_ERROR, systemError.getCode());

        Harness stale = new Harness();
        stale.publication.identity = identity(10L, 1000L, 5L);
        BusinessException staleConflict =
                assertThrows(
                        BusinessException.class, () -> stale.service.publish(10L, 4L, request));
        assertEquals(ResultCode.CONFLICT, staleConflict.getCode());
    }

    @Test
    void publishShouldRejectCurrentDefinitionOwnershipMismatch() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 11L, 4L);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.publish(
                                        10L,
                                        4L,
                                        request(List.of(node("task", TargetType.TASK, 3L)), null)));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
        assertEquals(List.of("barrier", "identity-lock", "definition-select"), harness.order);
    }

    @Test
    void publishShouldRejectRevisionOverflowAsConflict() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, Long.MAX_VALUE);
        harness.definitions.selected = definition(1000L, 10L, Long.MAX_VALUE);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.publish(
                                        10L,
                                        Long.MAX_VALUE,
                                        request(List.of(node("task", TargetType.TASK, 3L)), null)));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertEquals(List.of("barrier", "identity-lock", "definition-select"), harness.order);
    }

    @Test
    void danglingCurrentReferenceShouldBeSystemIntegrityError() {
        Harness harness = new Harness();
        harness.publication.lockedJobIds = List.of(10L, 20L);
        harness.snapshots.currentJobIds = List.of(10L);
        harness.snapshots.references = List.of(new JobReferenceRow(10L, 20L));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.create(
                                        request(List.of(node("task", TargetType.TASK, 3L)), null)));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("snapshot-references", harness.order.get(harness.order.size() - 1));
    }

    @Test
    void publishTargetMustAppearInCurrentSnapshot() {
        Harness harness = new Harness();
        harness.publication.lockedJobIds = List.of(10L);
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.publish(
                                        10L,
                                        4L,
                                        request(List.of(node("task", TargetType.TASK, 3L)), null)));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("snapshot-references", harness.order.get(harness.order.size() - 1));
    }

    @Test
    void publishShouldRejectCycleAgainstCurrentDefinitionSnapshot() {
        Harness harness = new Harness();
        harness.publication.lockedJobIds = List.of(10L, 20L);
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);
        harness.snapshots.currentJobIds = List.of(10L, 20L);
        harness.snapshots.references = List.of(new JobReferenceRow(20L, 10L));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.publish(
                                        10L,
                                        4L,
                                        request(
                                                List.of(node("nested", TargetType.JOB, 20L)),
                                                null)));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertEquals("snapshot-references", harness.order.get(harness.order.size() - 1));
    }

    @Test
    void everyInsertAndPointerFailureShouldStopAndBeSystemError() {
        for (FailurePoint failure : FailurePoint.values()) {
            Harness harness = new Harness();
            harness.failAt(failure);
            JobUpsertRequest request =
                    failure.isEdgeFailure()
                            ? request(
                                    List.of(
                                            node("a", TargetType.TASK, 3L),
                                            node("b", TargetType.TASK, 4L)),
                                    List.of(edge("a", "b")))
                            : request(List.of(node("task", TargetType.TASK, 3L)), null);

            BusinessException exception =
                    assertThrows(
                            BusinessException.class,
                            () -> harness.service.create(request),
                            failure::name);

            assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode(), failure::name);
            assertFalse(harness.order.isEmpty(), failure::name);
        }
    }

    @Test
    void advanceRowMismatchShouldFailAfterCompleteDefinitionWrite() {
        Harness harness = new Harness();
        harness.publication.lockedJobIds = List.of(10L);
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.publication.advanceRows = 0;
        harness.definitions.selected = definition(1000L, 10L, 4L);
        harness.snapshots.currentJobIds = List.of(10L);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                harness.service.publish(
                                        10L,
                                        4L,
                                        request(List.of(node("task", TargetType.TASK, 3L)), null)));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("pointer-advance", harness.order.get(harness.order.size() - 1));
    }

    @Test
    void mapperExceptionShouldPropagateWithoutContinuing() {
        Harness harness = new Harness();
        IllegalStateException failure = new IllegalStateException("lock");
        harness.publication.failure = failure;

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                harness.service.create(
                                        request(List.of(node("task", TargetType.TASK, 3L)), null)));

        assertSame(failure, thrown);
        assertEquals(List.of("barrier"), harness.order);
    }

    @Test
    void publicEntrypointsShouldDeclareRequiredWriteTransactions() throws Exception {
        for (Method method :
                List.of(
                        JobDefinitionPublicationService.class.getMethod(
                                "create", JobUpsertRequest.class),
                        JobDefinitionPublicationService.class.getMethod(
                                "publish", Long.class, Long.class, JobUpsertRequest.class))) {
            Transactional annotation = method.getAnnotation(Transactional.class);
            assertNotNull(annotation);
            assertFalse(annotation.readOnly());
            assertEquals(
                    org.springframework.transaction.annotation.Propagation.REQUIRED,
                    annotation.propagation());
        }
    }

    private JobUpsertRequest request(List<JobNodeRequest> nodes, List<JobEdgeRequest> edges) {
        JobUpsertRequest request = new JobUpsertRequest();
        request.setName("job");
        request.setTimeout(60);
        request.setEnabled(true);
        request.setDescription("description");
        request.setNodes(nodes);
        request.setEdges(edges);
        return request;
    }

    private JobNodeRequest node(String nodeId, TargetType type, Long referenceId) {
        JobNodePolicyRequest policy = new JobNodePolicyRequest();
        policy.setFailStrategy(FailStrategy.STOP);
        policy.setExecuteCondition(ExecuteCondition.ALL_SUCCESS);

        JobNodeRequest node = new JobNodeRequest();
        node.setNodeId(nodeId);
        node.setName(nodeId);
        node.setNodeType(type);
        node.setRefId(referenceId);
        node.setPolicy(policy);
        return node;
    }

    private JobEdgeRequest edge(String from, String to) {
        JobEdgeRequest edge = new JobEdgeRequest();
        edge.setFromNodeId(from);
        edge.setToNodeId(to);
        return edge;
    }

    private static JobPublicationIdentityRow identity(Long id, Long definitionId, Long revision) {
        JobPublicationIdentityRow identity = new JobPublicationIdentityRow();
        identity.setId(id);
        identity.setName("job");
        identity.setCurrentDefinitionId(definitionId);
        identity.setCurrentRevision(revision);
        identity.setRetired(false);
        return identity;
    }

    private static JobDefinition definition(Long id, Long jobId, Long revision) {
        JobDefinition definition = new JobDefinition();
        definition.setId(id);
        definition.setJobId(jobId);
        definition.setRevision(revision);
        return definition;
    }

    private enum FailurePoint {
        IDENTITY_ROWS,
        IDENTITY_ID,
        DEFINITION_ROWS,
        DEFINITION_ID,
        NODE_ROWS,
        NODE_ID,
        POLICY_ROWS,
        POLICY_ID,
        EDGE_ROWS,
        EDGE_ID,
        INITIAL_POINTER_ROWS;

        private boolean isEdgeFailure() {
            return this == EDGE_ROWS || this == EDGE_ID;
        }
    }

    private static final class Harness {
        private final List<String> order = new ArrayList<>();
        private final RecordingPublicationMapper publication =
                new RecordingPublicationMapper(order);
        private final RecordingSnapshotMapper snapshots = new RecordingSnapshotMapper(order);
        private final RecordingTaskLockMapper tasks = new RecordingTaskLockMapper(order);
        private final RecordingDefinitionMapper definitions = new RecordingDefinitionMapper(order);
        private final RecordingDefinitionGroupMapper groups = new RecordingDefinitionGroupMapper();
        private final RecordingNodeMapper nodes = new RecordingNodeMapper(order);
        private final RecordingPolicyMapper policies = new RecordingPolicyMapper(order);
        private final RecordingEdgeMapper edges = new RecordingEdgeMapper(order);
        private final RecordingIdentity identities = publication.identities;
        private final JobDefinitionPublicationService service =
                new JobDefinitionPublicationService(
                        new JobDefinitionValidator(new JobGraphDefinitionValidator()),
                        new NestedJobReferenceValidator(),
                        publication,
                        snapshots,
                        tasks,
                        definitions,
                        groups,
                        nodes,
                        policies,
                        edges);

        private void failAt(FailurePoint point) {
            switch (point) {
                case IDENTITY_ROWS -> publication.insertRows = 0;
                case IDENTITY_ID -> publication.generateIdentityId = false;
                case DEFINITION_ROWS -> definitions.insertRows = 0;
                case DEFINITION_ID -> definitions.generateId = false;
                case NODE_ROWS -> nodes.insertRows = 0;
                case NODE_ID -> nodes.generateId = false;
                case POLICY_ROWS -> policies.insertRows = 0;
                case POLICY_ID -> policies.generateId = false;
                case EDGE_ROWS -> edges.insertRows = 0;
                case EDGE_ID -> edges.generateId = false;
                case INITIAL_POINTER_ROWS -> publication.initialRows = 0;
            }
        }
    }

    private static final class RecordingDefinitionGroupMapper implements JobDefinitionGroupMapper {
        private final List<String> inserted = new ArrayList<>();

        @Override
        public int insert(Long definitionId, String groupCode, LocalDateTime createTime) {
            inserted.add(definitionId + ":" + groupCode);
            return 1;
        }

        @Override
        public List<DefinitionGroupRow> selectByDefinitionIds(List<Long> definitionIds) {
            return List.of();
        }
    }

    private static final class RecordingIdentity {
        private Job inserted;
    }

    private static final class RecordingPublicationMapper implements JobPublicationMapper {
        private final List<String> order;
        private final RecordingIdentity identities = new RecordingIdentity();
        private List<Long> lockedJobIds = List.of();
        private JobPublicationIdentityRow identity;
        private JobCurrentDefinitionAdvance advance;
        private Long initialDefinitionId;
        private int insertRows = 1;
        private int initialRows = 1;
        private int advanceRows = 1;
        private boolean generateIdentityId = true;
        private RuntimeException failure;

        private RecordingPublicationMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public List<Long> lockAllJobIdsForPublication() {
            order.add("barrier");
            if (failure != null) {
                throw failure;
            }
            return lockedJobIds;
        }

        @Override
        public JobPublicationIdentityRow selectIdentityForUpdate(Long jobId) {
            order.add("identity-lock");
            return identity;
        }

        @Override
        public int insertIdentity(Job identity) {
            order.add("identity-insert");
            if (generateIdentityId) {
                identity.setId(100L);
            }
            identities.inserted = identity;
            return insertRows;
        }

        @Override
        public int initializeCurrentDefinition(
                Long jobId, Long definitionId, LocalDateTime updateTime) {
            order.add("pointer-initialize");
            initialDefinitionId = definitionId;
            return initialRows;
        }

        @Override
        public int advanceCurrentDefinition(JobCurrentDefinitionAdvance advance) {
            order.add("pointer-advance");
            this.advance = advance;
            return advanceRows;
        }

        @Override
        public int retireIdentity(JobIdentityRetirement retirement) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingSnapshotMapper
            implements JobDefinitionReferenceSnapshotMapper {
        private final List<String> order;
        private List<Long> currentJobIds = List.of();
        private List<JobReferenceRow> references = List.of();

        private RecordingSnapshotMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public List<Long> selectAllCurrentJobIds() {
            order.add("snapshot-ids");
            return currentJobIds;
        }

        @Override
        public List<JobReferenceRow> selectCurrentJobReferences() {
            order.add("snapshot-references");
            return references;
        }

        @Override
        public Long selectCurrentJobDefinitionId(Long jobId) {
            return 1_000L + jobId;
        }
    }

    private static final class RecordingTaskLockMapper implements TaskReferenceLockMapper {
        private final List<String> order;
        private Set<Long> existingIds = Set.of(3L, 4L, 9L);
        private List<Long> requestedIds = List.of();

        private RecordingTaskLockMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public List<Long> lockExistingIds(List<Long> ids) {
            order.add("task-lock");
            requestedIds = List.copyOf(ids);
            return ids.stream().filter(existingIds::contains).toList();
        }

        @Override
        public Long selectCurrentTaskDefinitionId(Long taskId) {
            return 2_000L + taskId;
        }
    }

    private static final class RecordingDefinitionMapper implements JobDefinitionMapper {
        private final List<String> order;
        private JobDefinition selected;
        private JobDefinition inserted;
        private int insertRows = 1;
        private boolean generateId = true;

        private RecordingDefinitionMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public JobDefinition selectById(Long id) {
            order.add("definition-select");
            return selected;
        }

        @Override
        public int insert(JobDefinition definition) {
            order.add("definition-insert:" + definition.getRevision());
            if (generateId) {
                definition.setId(200L);
            }
            inserted = definition;
            return insertRows;
        }

        @Override
        public List<JobDefinition> selectListByJobId(Long jobId) {
            throw unsupported();
        }
    }

    private static final class RecordingNodeMapper implements JobDefinitionNodeMapper {
        private final List<String> order;
        private int insertRows = 1;
        private boolean generateId = true;
        private long nextId = 201L;

        private RecordingNodeMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public int insert(JobDefinitionNode node) {
            order.add("node-insert:" + node.getNodeKey());
            if (generateId) {
                node.setId(nextId++);
            }
            return insertRows;
        }

        @Override
        public List<JobDefinitionNode> selectListByDefinitionId(Long definitionId) {
            throw unsupported();
        }
    }

    private static final class RecordingPolicyMapper implements JobDefinitionNodePolicyMapper {
        private final List<String> order;
        private int insertRows = 1;
        private boolean generateId = true;
        private long nextId = 301L;

        private RecordingPolicyMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public int insert(JobDefinitionNodePolicy policy) {
            order.add("policy-insert:" + policy.getNodeId());
            if (generateId) {
                policy.setId(nextId++);
            }
            return insertRows;
        }

        @Override
        public List<JobDefinitionNodePolicy> selectListByDefinitionId(Long definitionId) {
            throw unsupported();
        }
    }

    private static final class RecordingEdgeMapper implements JobDefinitionEdgeMapper {
        private final List<String> order;
        private int insertRows = 1;
        private boolean generateId = true;
        private long nextId = 401L;

        private RecordingEdgeMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public int insert(JobDefinitionEdge edge) {
            order.add("edge-insert:" + edge.getFromNodeId() + "->" + edge.getToNodeId());
            if (generateId) {
                edge.setId(nextId++);
            }
            return insertRows;
        }

        @Override
        public List<JobDefinitionEdge> selectListByDefinitionId(Long definitionId) {
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by publication tests");
    }
}
