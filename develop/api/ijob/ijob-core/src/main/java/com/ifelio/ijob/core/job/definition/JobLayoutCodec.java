package com.ifelio.ijob.core.job.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ErrorDescriptor;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.job.JobEdgeRequest;
import com.ifelio.ijob.contract.job.JobLayout;
import com.ifelio.ijob.contract.job.JobNodeRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class JobLayoutCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PORTS = Set.of("left", "right", "top", "bottom");

    private JobLayoutCodec() {}

    public static void validate(JobLayout layout, List<JobNodeRequest> nodes) {
        if (layout == null) return;
        Set<String> keys =
                nodes.stream().map(JobNodeRequest::getNodeId).collect(Collectors.toSet());
        if (!Integer.valueOf(1).equals(layout.getVersion())
                || layout.getPositions() == null
                || layout.getPositions().size() > 500
                || !keys.containsAll(layout.getPositions().keySet())) throw invalid();
        for (JobLayout.Position position : layout.getPositions().values()) {
            if (position == null || !coordinate(position.getX()) || !coordinate(position.getY()))
                throw invalid();
        }
    }

    public static void validate(
            JobLayout layout, List<JobNodeRequest> nodes, List<JobEdgeRequest> edges) {
        validate(layout, nodes);
        if (layout == null || layout.getConnections() == null) return;
        if (layout.getConnections().size() > 5000) throw invalid();
        Set<List<String>> pairs =
                (edges == null ? List.<JobEdgeRequest>of() : edges)
                        .stream()
                                .map(e -> List.of(e.getFromNodeId(), e.getToNodeId()))
                                .collect(Collectors.toSet());
        Set<List<String>> seen = new HashSet<>();
        for (JobLayout.Connection connection : layout.getConnections()) {
            if (connection == null
                    || connection.getFromNodeId() == null
                    || connection.getToNodeId() == null
                    || !PORTS.contains(
                            connection.getSourceHandle() == null
                                    ? ""
                                    : connection.getSourceHandle())
                    || !PORTS.contains(
                            connection.getTargetHandle() == null
                                    ? ""
                                    : connection.getTargetHandle())) throw invalid();
            List<String> pair = List.of(connection.getFromNodeId(), connection.getToNodeId());
            if (!pairs.contains(pair) || !seen.add(pair)) throw invalid();
        }
    }

    private static boolean coordinate(Double value) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= 1000000;
    }

    private static BusinessException invalid() {
        return new BusinessException(
                new ErrorDescriptor(
                        ResultCode.PARAM_ERROR,
                        "IJOB-JOB-LAYOUT",
                        "error.job.layout",
                        "Canvas layout is invalid"));
    }

    public static String encode(JobLayout layout) {
        if (layout == null) return null;
        try {
            return MAPPER.writeValueAsString(layout);
        } catch (JsonProcessingException e) {
            throw invalid();
        }
    }

    public static JobLayout decode(String text) {
        if (text == null) return null;
        try {
            return MAPPER.readValue(text, JobLayout.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }
}
