package com.ifelio.ijob.core.execute.graph;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.DagConditionOperator;
import com.ifelio.ijob.contract.enums.GatewayMode;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.job.DagConditionExpression;
import com.ifelio.ijob.contract.job.DagConditionOperand;
import com.ifelio.ijob.contract.job.JobEdgeRequest;
import com.ifelio.ijob.contract.job.JobNodeRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

class JobGraphDefinitionValidatorTest {
    private final JobGraphDefinitionValidator validator = new JobGraphDefinitionValidator();

    @Test
    void shouldAcceptConnectedDagWithMultipleStartAndEndNodes() {
        List<JobNodeRequest> nodes =
                List.of(
                        node("first"),
                        node("second"),
                        node("join"),
                        node("left-end"),
                        node("right-end"));
        List<JobEdgeRequest> edges =
                List.of(
                        edge("first", "join"),
                        edge("second", "join"),
                        edge("join", "left-end"),
                        edge("join", "right-end"));

        assertDoesNotThrow(() -> validator.validate(nodes, edges));
    }

    @Test
    void shouldAcceptSingleNodeWithoutEdges() {
        assertDoesNotThrow(() -> validator.validate(List.of(node("only")), null));
    }

    @Test
    void shouldRequireAtLeastOneNode() {
        assertParameterError(
                () -> validator.validate(null, List.of()), "A job requires at least one node");
        assertParameterError(
                () -> validator.validate(List.of(), List.of()), "A job requires at least one node");
    }

    @Test
    void shouldEnforceNodeCountLimit() {
        List<JobNodeRequest> nodes =
                IntStream.rangeClosed(0, JobGraphDefinitionValidator.MAX_NODE_COUNT)
                        .mapToObj(index -> node("node-" + index))
                        .toList();

        assertParameterError(
                () -> validator.validate(nodes, List.of()), "Job node count must not exceed 500");
    }

    @Test
    void shouldRejectInvalidNodeDefinitions() {
        assertParameterError(
                () -> validator.validate(Collections.singletonList(null), List.of()),
                "Job node is required");
        assertParameterError(
                () -> validator.validate(List.of(node(" ")), List.of()), "Node code is required");
        assertParameterError(
                () -> validator.validate(List.of(node(" padded")), List.of()),
                "Node code must not have leading or trailing whitespace");
        assertParameterError(
                () ->
                        validator.validate(
                                List.of(
                                        node(
                                                "x"
                                                        .repeat(
                                                                JobGraphDefinitionValidator
                                                                                .MAX_NODE_CODE_LENGTH
                                                                        + 1))),
                                List.of()),
                "Node code length must not exceed 64");
    }

    @Test
    void shouldRejectDuplicateNodeCodes() {
        assertParameterError(
                () -> validator.validate(List.of(node("same"), node("same")), List.of()),
                "Duplicate node code: same");
    }

    @Test
    void shouldEnforceEdgeCountLimit() {
        List<JobEdgeRequest> edges =
                Collections.nCopies(
                        JobGraphDefinitionValidator.MAX_EDGE_COUNT + 1, edge("first", "second"));

        assertParameterError(
                () -> validator.validate(List.of(node("first"), node("second")), edges),
                "Job edge count must not exceed 5,000");
    }

    @Test
    void shouldRejectInvalidEdgeDefinitions() {
        List<JobNodeRequest> nodes = List.of(node("first"), node("second"));

        assertParameterError(
                () -> validator.validate(nodes, Collections.singletonList(null)),
                "Job edge is required");
        assertParameterError(
                () -> validator.validate(nodes, List.of(edge(null, "second"))),
                "Edge source is required");
        assertParameterError(
                () -> validator.validate(nodes, List.of(edge("first", null))),
                "Edge target is required");
        assertParameterError(
                () -> validator.validate(nodes, List.of(edge("missing", "second"))),
                "Edge source does not exist: missing");
        assertParameterError(
                () -> validator.validate(nodes, List.of(edge("first", "missing"))),
                "Edge target does not exist: missing");
    }

    @Test
    void shouldRejectSelfLoop() {
        assertParameterError(
                () -> validator.validate(List.of(node("only")), List.of(edge("only", "only"))),
                "Job edge must not reference itself: only");
    }

    @Test
    void shouldRejectDuplicateEdges() {
        assertParameterError(
                () ->
                        validator.validate(
                                List.of(node("first"), node("second")),
                                List.of(edge("first", "second"), edge("first", "second"))),
                "Duplicate job edge: first -> second");
    }

    @Test
    void shouldRejectCycle() {
        assertParameterError(
                () ->
                        validator.validate(
                                List.of(node("first"), node("second"), node("third")),
                                List.of(
                                        edge("first", "second"),
                                        edge("second", "third"),
                                        edge("third", "first"))),
                "Job graph must not contain cycles");
    }

    @Test
    void shouldRejectIsolatedNodeInMultiNodeGraph() {
        assertParameterError(
                () ->
                        validator.validate(
                                List.of(node("first"), node("second"), node("isolated")),
                                List.of(edge("first", "second"))),
                "Multi-node job must not contain an isolated node: isolated");
    }

    @Test
    void shouldAcceptExclusiveGatewayWithDirectUpstreamConditionAndDefaultRoute() {
        List<JobNodeRequest> nodes =
                List.of(node("prepare"), gateway("route"), node("fast"), node("normal"));
        List<JobEdgeRequest> edges =
                List.of(
                        edge("prepare", "route"),
                        route("route", "fast", "fast", 10, false, condition("nodes.prepare.score")),
                        route("route", "normal", "default", 20, true, null));

        assertDoesNotThrow(() -> validator.validate(nodes, edges));
    }

    @Test
    void shouldRejectGatewayWithoutExactlyOneDefaultRoute() {
        List<JobNodeRequest> nodes = List.of(gateway("route"), node("left"), node("right"));
        List<JobEdgeRequest> edges =
                List.of(
                        route("route", "left", "left", 10, false, condition("input.value")),
                        route("route", "right", "right", 20, false, condition("input.value")));

        assertParameterError(
                () -> validator.validate(nodes, edges),
                "Gateway requires exactly one default route");
    }

    @Test
    void shouldRejectRouteFieldsOnExecutionEdgeAndNonDirectNodeReference() {
        JobEdgeRequest plainRoute = route("first", "second", "invalid", 1, true, null);
        assertParameterError(
                () ->
                        validator.validate(
                                List.of(node("first"), node("second")), List.of(plainRoute)),
                "Conditional route fields are only allowed on gateway outgoing edges");

        List<JobNodeRequest> nodes =
                List.of(
                        node("not-parent"),
                        node("prepare"),
                        gateway("route"),
                        node("fast"),
                        node("normal"));
        List<JobEdgeRequest> edges =
                List.of(
                        edge("not-parent", "prepare"),
                        edge("prepare", "route"),
                        route(
                                "route",
                                "fast",
                                "fast",
                                10,
                                false,
                                condition("nodes.not-parent.score")),
                        route("route", "normal", "default", 20, true, null));
        assertParameterError(
                () -> validator.validate(nodes, edges),
                "Gateway condition may only reference a direct upstream node");
    }

    private void assertParameterError(Executable executable, String message) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertEquals(message, exception.getMessage());
    }

    private JobNodeRequest node(String nodeCode) {
        JobNodeRequest node = new JobNodeRequest();
        node.setNodeId(nodeCode);
        return node;
    }

    private JobNodeRequest gateway(String nodeCode) {
        JobNodeRequest node = node(nodeCode);
        node.setKind(JobNodeKind.GATEWAY);
        node.setGateway(new com.ifelio.ijob.contract.job.JobGatewayRequest());
        node.getGateway().setMode(GatewayMode.EXCLUSIVE);
        return node;
    }

    private JobEdgeRequest edge(String fromNodeCode, String toNodeCode) {
        JobEdgeRequest edge = new JobEdgeRequest();
        edge.setFromNodeId(fromNodeCode);
        edge.setToNodeId(toNodeCode);
        return edge;
    }

    private JobEdgeRequest route(
            String fromNodeCode,
            String toNodeCode,
            String routeKey,
            int priority,
            boolean defaultRoute,
            DagConditionExpression condition) {
        JobEdgeRequest edge = edge(fromNodeCode, toNodeCode);
        edge.setRouteKey(routeKey);
        edge.setPriority(priority);
        edge.setDefaultRoute(defaultRoute);
        edge.setCondition(condition);
        return edge;
    }

    private DagConditionExpression condition(String variable) {
        DagConditionOperand left = new DagConditionOperand();
        left.setVar(variable);
        DagConditionOperand right = new DagConditionOperand();
        right.setLiteral(10);
        DagConditionExpression expression = new DagConditionExpression();
        expression.setOp(DagConditionOperator.GE);
        expression.setLeft(left);
        expression.setRight(right);
        return expression;
    }
}
