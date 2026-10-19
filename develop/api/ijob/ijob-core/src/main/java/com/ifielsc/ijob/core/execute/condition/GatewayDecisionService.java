package com.ifielsc.ijob.core.execute.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.GatewayDecisionStatus;
import com.ifielsc.ijob.core.execute.graph.JobExecutionEdge;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.infra.coordination.work.ExecutionOwnershipLostException;
import com.ifielsc.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;
import com.ifielsc.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;
import com.ifielsc.ijob.infra.mapper.runtime.TriggerMapper;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GatewayDecisionService implements GatewayDecisionResolver {
    public static final String EVALUATION_ERROR_CODE = "IJOB-DAG-0500";

    private final DagGatewayDecisionMapper decisionMapper;
    private final InstanceMapper instanceMapper;
    private final TriggerMapper triggerMapper;
    private final VariableSnapshotService snapshotService;
    private final DagConditionEvaluator evaluator;
    private final ObjectMapper canonicalMapper;

    public GatewayDecisionService(
            DagGatewayDecisionMapper decisionMapper,
            InstanceMapper instanceMapper,
            TriggerMapper triggerMapper,
            VariableSnapshotService snapshotService,
            DagConditionEvaluator evaluator,
            ObjectMapper objectMapper) {
        this.decisionMapper = decisionMapper;
        this.instanceMapper = instanceMapper;
        this.triggerMapper = triggerMapper;
        this.snapshotService = snapshotService;
        this.evaluator = evaluator;
        this.canonicalMapper =
                objectMapper
                        .copy()
                        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    public GatewaySelection select(
            Long jobInstanceId,
            JobExecutionNode gateway,
            JobExecutionPlan plan,
            Map<Long, Instance> nodeInstances,
            boolean forceReevaluate) {
        Instance jobInstance = requireJobInstance(jobInstanceId, gateway);
        Trigger trigger = requireTrigger(jobInstance.getTriggerId());
        Map<String, Object> context =
                buildContext(jobInstance, trigger, gateway, plan, nodeInstances);
        String digest = digest(context);
        List<DagGatewayDecision> decisions =
                decisionMapper.selectByJobInstanceAndNode(jobInstanceId, gateway.id());
        if (decisions == null) {
            throw systemError("Gateway decision query returned null");
        }
        DagGatewayDecision latest = decisions.isEmpty() ? null : decisions.get(0);
        if (latest != null && !forceReevaluate) {
            return reuse(latest, digest, gateway, plan);
        }

        int evaluationNo = latest == null ? 1 : Math.addExact(latest.getEvaluationNo(), 1);
        Long selectedEdgeId;
        try {
            selectedEdgeId = evaluate(gateway, plan, context);
        } catch (RuntimeException exception) {
            persist(
                    jobInstance,
                    gateway,
                    evaluationNo,
                    null,
                    digest,
                    GatewayDecisionStatus.FAILED,
                    EVALUATION_ERROR_CODE);
            throw exception;
        }
        persist(
                jobInstance,
                gateway,
                evaluationNo,
                selectedEdgeId.toString(),
                digest,
                GatewayDecisionStatus.SUCCESS,
                null);
        return new GatewaySelection(Set.of(selectedEdgeId), false);
    }

    public Set<Long> failedGatewayNodeIds(Long jobInstanceId) {
        List<DagGatewayDecision> rows = decisionMapper.selectByJobInstanceId(jobInstanceId);
        if (rows == null) {
            throw systemError("Gateway decision query returned null");
        }
        Map<Long, DagGatewayDecision> latest = new LinkedHashMap<>();
        rows.forEach(row -> latest.putIfAbsent(row.getDefinitionNodeId(), row));
        return latest.values().stream()
                .filter(row -> row.getStatus() == GatewayDecisionStatus.FAILED)
                .map(DagGatewayDecision::getDefinitionNodeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private GatewaySelection reuse(
            DagGatewayDecision decision,
            String digest,
            JobExecutionNode gateway,
            JobExecutionPlan plan) {
        if (!digest.equals(decision.getContextDigest())
                || decision.getEvaluatorVersion() == null
                || decision.getEvaluatorVersion() != DagConditionEvaluator.EVALUATOR_VERSION) {
            throw systemError("Persisted gateway decision context does not match");
        }
        if (decision.getStatus() == GatewayDecisionStatus.FAILED) {
            throw systemError("Persisted gateway decision failed");
        }
        if (decision.getStatus() != GatewayDecisionStatus.SUCCESS
                || decision.getSelectedEdgeIds() == null) {
            throw systemError("Persisted gateway decision is invalid");
        }
        Long edgeId;
        try {
            edgeId = Long.valueOf(decision.getSelectedEdgeIds());
        } catch (NumberFormatException exception) {
            throw systemError("Persisted gateway route selection is invalid");
        }
        JobExecutionEdge edge = plan.edgeMap().get(edgeId);
        if (edge == null || !gateway.id().equals(edge.fromNodeId())) {
            throw systemError("Persisted gateway route is not part of the execution plan");
        }
        return new GatewaySelection(Set.of(edgeId), true);
    }

    private Long evaluate(
            JobExecutionNode gateway, JobExecutionPlan plan, Map<String, Object> context) {
        List<JobExecutionEdge> routes = new ArrayList<>(plan.outgoingEdgeMap().get(gateway.id()));
        routes.sort(
                Comparator.comparing(JobExecutionEdge::priority)
                        .thenComparing(JobExecutionEdge::id));
        JobExecutionEdge defaultRoute = null;
        for (JobExecutionEdge route : routes) {
            if (route.defaultRoute()) {
                defaultRoute = route;
            } else if (evaluator.evaluate(route.condition(), context)) {
                return route.id();
            }
        }
        if (defaultRoute == null) {
            throw systemError("Gateway has no matching or default route");
        }
        return defaultRoute.id();
    }

    private Map<String, Object> buildContext(
            Instance jobInstance,
            Trigger trigger,
            JobExecutionNode gateway,
            JobExecutionPlan plan,
            Map<Long, Instance> nodeInstances) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(
                "input", snapshotService.deserializeUserVariables(jobInstance.getInputVariables()));

        Map<String, Object> nodes = new LinkedHashMap<>();
        for (Long parentId : plan.parentMap().get(gateway.id())) {
            JobExecutionNode parent = plan.nodeMap().get(parentId);
            Instance parentInstance = nodeInstances.get(parentId);
            if (parentInstance != null) {
                nodes.put(
                        parent.nodeKey(),
                        snapshotService.deserializeUserVariables(
                                parentInstance.getOutputVariables()));
            }
        }
        context.put("nodes", nodes);

        Map<String, Object> triggerValues = new LinkedHashMap<>();
        triggerValues.put("id", trigger.getId());
        triggerValues.put("type", trigger.getTriggerType().name());
        triggerValues.put(
                "time", trigger.getTriggerTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        triggerValues.put(
                "date", trigger.getTriggerTime().format(DateTimeFormatter.ISO_LOCAL_DATE));
        context.put("trigger", triggerValues);
        return context;
    }

    private String digest(Map<String, Object> context) {
        try {
            byte[] payload = canonicalMapper.writeValueAsBytes(context);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw systemError("Gateway condition context cannot be digested");
        }
    }

    private void persist(
            Instance jobInstance,
            JobExecutionNode gateway,
            int evaluationNo,
            String selectedEdgeIds,
            String digest,
            GatewayDecisionStatus status,
            String errorCode) {
        DagGatewayDecision decision = new DagGatewayDecision();
        decision.setJobInstanceId(jobInstance.getId());
        decision.setDefinitionNodeId(gateway.id());
        decision.setEvaluationNo(evaluationNo);
        decision.setSelectedEdgeIds(selectedEdgeIds);
        decision.setContextDigest(digest);
        decision.setEvaluatorVersion(DagConditionEvaluator.EVALUATOR_VERSION);
        decision.setStatus(status);
        decision.setErrorCode(errorCode);
        decision.setExecutionFence(jobInstance.getExecutionFence());
        decision.setCreateTime(LocalDateTime.now());
        if (decisionMapper.insertIfOwned(
                        decision, jobInstance.getRootInstanceId(), jobInstance.getTriggerId())
                != 1) {
            throw new ExecutionOwnershipLostException(jobInstance.getTriggerId());
        }
    }

    private Instance requireJobInstance(Long jobInstanceId, JobExecutionNode gateway) {
        Instance instance = instanceMapper.selectById(jobInstanceId);
        if (instance == null
                || instance.getRootInstanceId() == null
                || instance.getTriggerId() == null
                || instance.getExecutionFence() == null
                || instance.getDefinitionId() == null
                || gateway == null
                || !instance.getDefinitionId().equals(gateway.definitionId())) {
            throw systemError("Gateway job instance snapshot is invalid");
        }
        return instance;
    }

    private Trigger requireTrigger(Long triggerId) {
        Trigger trigger = triggerMapper.selectById(triggerId);
        if (trigger == null
                || trigger.getId() == null
                || trigger.getTriggerType() == null
                || trigger.getTriggerTime() == null) {
            throw systemError("Gateway trigger snapshot is invalid");
        }
        return trigger;
    }

    private BusinessException systemError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }
}
