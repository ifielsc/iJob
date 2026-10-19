// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.runtime.RunGraphDTO;
import com.ifielsc.ijob.core.job.definition.JobLayoutCodec;
import com.ifielsc.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifielsc.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RunGraphQueryService {
    private final InstanceMapper instances;
    private final JobDefinitionMapper definitions;
    private final JobDefinitionNodeMapper nodes;
    private final JobDefinitionEdgeMapper edges;
    private final DagGatewayDecisionMapper decisions;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public RunGraphDTO get(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR);
        var job = instances.selectSummaryById(id);
        if (job == null)
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        if (job.getTargetType() != TargetType.JOB || job.getDefinitionId() == null)
            throw new BusinessException(ResultCode.CONFLICT);
        var definition = definitions.selectById(job.getDefinitionId());
        if (definition == null || !job.getTargetId().equals(definition.getJobId()))
            throw new BusinessException(ResultCode.NOT_FOUND);
        var children = new HashMap<Long, Instance>();
        for (var child : instances.selectByParentInstanceId(id)) {
            if (!id.equals(child.getParentInstanceId()) || child.getDefinitionNodeId() == null)
                continue;
            children.merge(
                    child.getDefinitionNodeId(), child, (a, b) -> a.getId() > b.getId() ? a : b);
        }
        var latest = new HashMap<Long, DagGatewayDecision>();
        for (var decision : decisions.selectByJobInstanceId(id)) {
            if (!id.equals(decision.getJobInstanceId())) continue;
            latest.merge(
                    decision.getDefinitionNodeId(),
                    decision,
                    (a, b) ->
                            a.getEvaluationNo() > b.getEvaluationNo()
                                    ? a
                                    : a.getEvaluationNo().equals(b.getEvaluationNo())
                                                    && a.getId() > b.getId()
                                            ? a
                                            : b);
        }
        var selectedEdges = new HashSet<String>();
        for (var decision : latest.values()) {
            if (decision.getSelectedEdgeIds() == null) continue;
            try {
                var selected = json.readTree(decision.getSelectedEdgeIds());
                // EXCLUSIVE decisions persist a single decimal edge ID; older graph
                // snapshots may contain an array. Neither form may pass through a double.
                if (selected.isIntegralNumber()
                        && selected.canConvertToLong()
                        && selected.longValue() > 0) {
                    selectedEdges.add(selected.asText());
                } else if (selected.isArray()) {
                    selected.forEach(edge -> selectedEdges.add(edge.asText()));
                } else {
                    throw new IllegalArgumentException();
                }
            } catch (Exception e) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
        }
        boolean finished =
                Set.of("SUCCESS", "FAILED", "STOPPED", "TIMED_OUT", "SKIPPED")
                        .contains(job.getStatus().name());
        var graphNodes =
                nodes.selectListByDefinitionId(definition.getId()).stream()
                        .map(
                                node -> {
                                    var child = children.get(node.getId());
                                    var decision = latest.get(node.getId());
                                    String status =
                                            child != null
                                                    ? child.getStatus().name()
                                                    : decision != null
                                                            ? decision.getStatus().name()
                                                            : finished
                                                                    ? "NOT_EXECUTED"
                                                                    : "NOT_STARTED";
                                    return new RunGraphDTO.Node(
                                            text(node.getId()),
                                            node.getNodeKey(),
                                            node.getName(),
                                            text(node.getNodeKind()),
                                            text(node.getNodeType()),
                                            text(node.getRefId()),
                                            child == null ? null : text(child.getId()),
                                            status,
                                            child == null ? null : child.getProgress(),
                                            child == null ? null : child.getRetryCount());
                                })
                        .toList();
        var graphEdges =
                edges.selectListByDefinitionId(definition.getId()).stream()
                        .map(
                                edge ->
                                        new RunGraphDTO.Edge(
                                                text(edge.getId()),
                                                text(edge.getFromNodeId()),
                                                text(edge.getToNodeId()),
                                                edge.getRouteKey(),
                                                edge.getRoutePriority(),
                                                Boolean.TRUE.equals(edge.getDefaultRoute()),
                                                selectedEdges.contains(text(edge.getId()))))
                        .toList();
        return new RunGraphDTO(
                text(job.getId()),
                text(job.getRootInstanceId()),
                text(job.getTargetId()),
                text(definition.getId()),
                definition.getName(),
                job.getStatus().name(),
                JobLayoutCodec.decode(definition.getLayout()),
                graphNodes,
                graphEdges);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
