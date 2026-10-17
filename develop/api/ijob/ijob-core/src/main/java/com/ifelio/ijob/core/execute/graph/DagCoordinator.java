package com.ifelio.ijob.core.execute.graph;

import com.ifelio.ijob.common.diagnostic.DiagnosticContext;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.core.execute.DagNodeExecutor;
import com.ifelio.ijob.core.execute.ExecutionTreeControl;
import com.ifelio.ijob.core.execute.NodeExecuteResult;
import com.ifelio.ijob.core.execute.NodeExecuteStatus;
import com.ifelio.ijob.core.execute.condition.GatewayDecisionResolver;
import com.ifelio.ijob.core.execute.condition.GatewaySelection;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

@Component
public class DagCoordinator {
    private static final long CONTROL_POLL_MILLIS = 200L;

    private final DependencyEvaluator dependencyEvaluator;
    private final DagNodeExecutor nodeExecutor;
    private final ExecutionTreeControl executionTreeControl;
    private final GatewayDecisionResolver gatewayDecisionResolver;
    private final ExecutorService dagNodeExecutor;

    @Autowired
    public DagCoordinator(
            DependencyEvaluator dependencyEvaluator,
            DagNodeExecutor nodeExecutor,
            ExecutionTreeControl executionTreeControl,
            GatewayDecisionResolver gatewayDecisionResolver,
            @Qualifier("dagNodeExecutor") ExecutorService dagNodeExecutor) {
        this.dependencyEvaluator = dependencyEvaluator;
        this.nodeExecutor = nodeExecutor;
        this.executionTreeControl = executionTreeControl;
        this.gatewayDecisionResolver = gatewayDecisionResolver;
        this.dagNodeExecutor = dagNodeExecutor;
    }

    public DagCoordinator(
            DependencyEvaluator dependencyEvaluator,
            DagNodeExecutor nodeExecutor,
            ExecutionTreeControl executionTreeControl,
            ExecutorService dagNodeExecutor) {
        this(dependencyEvaluator, nodeExecutor, executionTreeControl, null, dagNodeExecutor);
    }

    /**
     * Advances a DAG in short, serialized turns. Neither an orchestration worker nor a leaf worker
     * waits for descendants. At most one delayed turn is outstanding per active DAG.
     */
    public CompletableFuture<DagCoordinationResult> coordinateAsync(
            Long jobInstanceId,
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> historyResults,
            Set<Long> inactiveHistoryNodeIds,
            Set<Long> reevaluateGatewayNodeIds,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext,
            IntConsumer progressListener,
            Executor continuationExecutor,
            long startedNanos,
            Integer timeoutSeconds) {
        return new AsyncCoordination(
                        jobInstanceId,
                        plan,
                        historyResults,
                        inactiveHistoryNodeIds,
                        reevaluateGatewayNodeIds,
                        nodeInstanceMap,
                        controlContext,
                        progressListener,
                        continuationExecutor,
                        startedNanos,
                        timeoutSeconds)
                .start();
    }

    private final class AsyncCoordination {
        private final Long jobInstanceId;
        private final JobExecutionPlan plan;
        private final Map<Long, Instance> instances;
        private final TaskContext context;
        private final IntConsumer progress;
        private final Executor continuations;
        private final long startedNanos;
        private final Integer timeout;
        private final Map<Long, InstanceStatus> results;
        private final Set<Long> inactive;
        private final Set<Long> reevaluate;
        private final Map<Long, EdgeActivation> edges;
        private final Map<Long, Integer> dependencies;
        private final Set<Long> scheduled;
        private final Set<Long> suspended = new HashSet<>();
        private final Queue<JobExecutionNode> ready = new ArrayDeque<>();
        private final Map<Long, AsyncNode> inFlight = new ConcurrentHashMap<>();
        private final CompletableFuture<DagCoordinationResult> completion =
                new CompletableFuture<>();
        private final Runnable turn;
        private final AtomicInteger pendingTurns = new AtomicInteger();
        private final AtomicBoolean pollScheduled = new AtomicBoolean();

        AsyncCoordination(
                Long jobInstanceId,
                JobExecutionPlan plan,
                Map<Long, InstanceStatus> history,
                Set<Long> inactiveHistory,
                Set<Long> reevaluate,
                Map<Long, Instance> instances,
                TaskContext context,
                IntConsumer progress,
                Executor continuations,
                long startedNanos,
                Integer timeout) {
            this.jobInstanceId = jobInstanceId;
            this.plan = plan;
            this.instances = instances;
            this.context = context;
            this.progress = progress;
            this.continuations = continuations;
            this.startedNanos = startedNanos;
            this.timeout = timeout;
            this.results = validateAndCopyHistory(plan, history);
            this.inactive = validateInactiveHistory(results, inactiveHistory);
            this.reevaluate = reevaluate == null ? Set.of() : Set.copyOf(reevaluate);
            this.edges = initializeEdgeStates(plan, results, inactive);
            this.dependencies = remainingDependencies(plan, results);
            this.scheduled = new HashSet<>(results.keySet());
            this.turn = DiagnosticContext.wrap(this::drainTurns);
            completion.whenComplete((value, error) -> inFlight.values().forEach(AsyncNode::cancel));
        }

        CompletableFuture<DagCoordinationResult> start() {
            progress.accept(results.size());
            if (containsStopFailure(plan, results)) {
                completion.complete(new DagCoordinationResult(results, true));
            } else {
                enqueueReadyNodes(
                        plan,
                        results,
                        dependencies,
                        edges,
                        !plan.edges().isEmpty(),
                        scheduled,
                        ready);
                requestTurn();
            }
            return completion;
        }

        private void advance() {
            if (completion.isDone()) return;
            try {
                if (timeout != null
                        && timeout > 0
                        && System.nanoTime() - startedNanos >= TimeUnit.SECONDS.toNanos(timeout)) {
                    executionTreeControl.requestTimeout(jobInstanceId);
                    throw new StopException();
                }
                context.checkStop();
                if (context.getHandle().isPauseRequested()) {
                    scheduleTurn();
                    return;
                }
                for (AsyncNode active : List.copyOf(inFlight.values())) {
                    if (!active.result.isDone()) continue;
                    inFlight.remove(active.node.id());
                    NodeExecuteResult result = resolveResult(active.result, active.node, context);
                    context.logInfo(
                            "Job node resolved; definitionNodeId="
                                    + active.node.id()
                                    + "; status="
                                    + result.status());
                    if (result.status() == NodeExecuteStatus.WAITING_EVENT) {
                        suspended.add(active.node.id());
                        continue;
                    }
                    results.put(active.node.id(), toInstanceStatus(result.status()));
                    if (result.inactivePath()) inactive.add(active.node.id());
                    progress.accept(results.size());
                    if (result.failedOrTimedOut() && result.failStrategy() == FailStrategy.STOP) {
                        executionTreeControl.requestAbortDescendants(jobInstanceId);
                        completion.complete(new DagCoordinationResult(results, true));
                        return;
                    }
                    releaseChildren(
                            active.node,
                            result,
                            plan,
                            results,
                            dependencies,
                            edges,
                            !plan.edges().isEmpty(),
                            scheduled,
                            ready);
                }
                while (!ready.isEmpty() && !completion.isDone()) {
                    context.checkStop();
                    JobExecutionNode node = ready.remove();
                    AsyncNode active = new AsyncNode(node);
                    inFlight.put(node.id(), active);
                    active.result.whenComplete((value, error) -> requestTurn());
                    Map<Long, InstanceStatus> snapshot = Map.copyOf(results);
                    Map<Long, EdgeActivation> edgeSnapshot = Map.copyOf(edges);
                    active.worker =
                            dagNodeExecutor.submit(
                                    () -> {
                                        if (completion.isDone() || active.result.isCancelled())
                                            return;
                                        try {
                                            var child =
                                                    executeNodeAsync(
                                                            jobInstanceId,
                                                            node,
                                                            plan,
                                                            snapshot,
                                                            edgeSnapshot,
                                                            !plan.edges().isEmpty(),
                                                            reevaluate.contains(node.id()),
                                                            instances,
                                                            context);
                                            active.child = child;
                                            if (active.result.isCancelled()) child.cancel(true);
                                            child.whenComplete(
                                                    (value, error) -> {
                                                        if (error == null)
                                                            active.result.complete(value);
                                                        else
                                                            active.result.completeExceptionally(
                                                                    error);
                                                    });
                                        } catch (Throwable error) {
                                            active.result.completeExceptionally(error);
                                        }
                                    });
                    if (completion.isDone()) active.cancel();
                    context.logInfo("Job node dispatched; definitionNodeId=" + node.id());
                }
                if (results.size() == plan.nodes().size()) {
                    completion.complete(new DagCoordinationResult(results, false));
                } else if (inFlight.isEmpty()) {
                    if (!suspended.isEmpty())
                        completion.complete(new DagCoordinationResult(results, false, true));
                    else throw systemError("Job DAG coordinator is stalled");
                } else scheduleTurn();
            } catch (Throwable failure) {
                fail(failure);
            }
        }

        private void requestTurn() {
            if (completion.isDone() || pendingTurns.getAndIncrement() != 0) return;
            try {
                continuations.execute(turn);
            } catch (RuntimeException rejected) {
                pendingTurns.set(0);
                fail(rejected);
            }
        }

        private void drainTurns() {
            int processed = 1;
            do {
                advance();
                processed = pendingTurns.addAndGet(-processed);
            } while (processed != 0 && !completion.isDone());
        }

        private void scheduleTurn() {
            if (!pollScheduled.compareAndSet(false, true)) return;
            CompletableFuture.delayedExecutor(
                            CONTROL_POLL_MILLIS, TimeUnit.MILLISECONDS, Runnable::run)
                    .execute(
                            () -> {
                                pollScheduled.set(false);
                                requestTurn();
                            });
        }

        private void fail(Throwable failure) {
            if (completion.isDone()) return;
            try {
                executionTreeControl.requestAbortDescendants(jobInstanceId);
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            completion.completeExceptionally(failure);
        }
    }

    private static final class AsyncNode {
        private final JobExecutionNode node;
        private final CompletableFuture<NodeExecuteResult> result = new CompletableFuture<>();
        private volatile Future<?> worker;
        private volatile CompletableFuture<NodeExecuteResult> child;

        AsyncNode(JobExecutionNode node) {
            this.node = node;
        }

        void cancel() {
            result.cancel(true);
            Future<?> running = worker;
            if (running != null) running.cancel(true);
            var nested = child;
            if (nested != null) nested.cancel(true);
        }
    }

    private CompletableFuture<NodeExecuteResult> executeNodeAsync(
            Long jobInstanceId,
            JobExecutionNode node,
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> dependencySnapshot,
            Map<Long, EdgeActivation> edgeStates,
            boolean routed,
            boolean forceGatewayReevaluation,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext) {
        checkControl(controlContext);
        List<Long> parents = activeParents(node, plan, edgeStates, routed);
        List<JobExecutionNode> upstream = parents.stream().map(plan.nodeMap()::get).toList();
        if (node.kind() == JobNodeKind.GATEWAY
                || routed && branchNotSelected(node, plan, edgeStates)
                || !dependencyEvaluator.shouldExecute(node, parents, dependencySnapshot)) {
            return CompletableFuture.completedFuture(
                    executeNode(
                            jobInstanceId,
                            node,
                            plan,
                            dependencySnapshot,
                            edgeStates,
                            routed,
                            forceGatewayReevaluation,
                            nodeInstanceMap,
                            controlContext));
        }
        return nodeExecutor.executeAsync(
                node, jobInstanceId, nodeInstanceMap, controlContext, upstream);
    }

    private NodeExecuteResult executeNode(
            Long jobInstanceId,
            JobExecutionNode node,
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> dependencySnapshot,
            Map<Long, EdgeActivation> edgeStates,
            boolean routed,
            boolean forceGatewayReevaluation,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext) {
        checkControl(controlContext);
        try {
            List<Long> activeParents = activeParents(node, plan, edgeStates, routed);
            List<JobExecutionNode> upstreamNodes =
                    activeParents.stream().map(plan.nodeMap()::get).toList();
            if (routed && branchNotSelected(node, plan, edgeStates)) {
                if (node.kind() == JobNodeKind.GATEWAY) {
                    return NodeExecuteResult.branchNotSelected(node);
                }
                return nodeExecutor.skipBranch(node, jobInstanceId, nodeInstanceMap, upstreamNodes);
            }
            if (!dependencyEvaluator.shouldExecute(node, activeParents, dependencySnapshot)) {
                return nodeExecutor.skip(node, jobInstanceId, nodeInstanceMap, upstreamNodes);
            }
            if (node.kind() == JobNodeKind.GATEWAY) {
                if (gatewayDecisionResolver == null) {
                    throw systemError("Gateway decision service is unavailable");
                }
                GatewaySelection selection =
                        gatewayDecisionResolver.select(
                                jobInstanceId,
                                node,
                                plan,
                                nodeInstanceMap,
                                forceGatewayReevaluation);
                return NodeExecuteResult.gateway(node, selection.selectedEdgeIds());
            }
            return nodeExecutor.execute(
                    node, jobInstanceId, nodeInstanceMap, controlContext, upstreamNodes);
        } catch (StopException exception) {
            throw exception;
        } catch (Exception exception) {
            return NodeExecuteResult.failed(node, exception);
        }
    }

    private boolean branchNotSelected(
            JobExecutionNode node, JobExecutionPlan plan, Map<Long, EdgeActivation> edgeStates) {
        List<JobExecutionEdge> incoming = plan.incomingEdgeMap().get(node.id());
        return !incoming.isEmpty()
                && incoming.stream()
                        .allMatch(edge -> edgeStates.get(edge.id()) == EdgeActivation.INACTIVE);
    }

    private List<Long> activeParents(
            JobExecutionNode node,
            JobExecutionPlan plan,
            Map<Long, EdgeActivation> edgeStates,
            boolean routed) {
        if (!routed) {
            return plan.parentMap().get(node.id());
        }
        return plan.incomingEdgeMap().get(node.id()).stream()
                .filter(edge -> edgeStates.get(edge.id()) == EdgeActivation.ACTIVE)
                .map(JobExecutionEdge::fromNodeId)
                .toList();
    }

    private NodeExecuteResult resolveResult(
            Future<NodeExecuteResult> completedFuture,
            JobExecutionNode node,
            TaskContext controlContext) {
        try {
            NodeExecuteResult result = completedFuture.get();
            if (result == null || !node.id().equals(result.nodeId()) || result.status() == null) {
                return NodeExecuteResult.failed(node, systemError("Job node result is invalid"));
            }
            return result;
        } catch (CancellationException exception) {
            return NodeExecuteResult.of(node, NodeExecuteStatus.STOPPED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StopException();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof StopException) {
                controlContext.checkStop();
                return NodeExecuteResult.of(node, NodeExecuteStatus.STOPPED);
            }
            return NodeExecuteResult.failed(node, cause);
        }
    }

    private void releaseChildren(
            JobExecutionNode completedNode,
            NodeExecuteResult result,
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> nodeResults,
            Map<Long, Integer> remainingDependencies,
            Map<Long, EdgeActivation> edgeStates,
            boolean routed,
            Set<Long> scheduledNodeIds,
            Queue<JobExecutionNode> readyNodes) {
        if (routed) {
            resolveOutgoingEdges(completedNode, result, plan, edgeStates);
        } else {
            for (Long childId : plan.childMap().get(completedNode.id())) {
                remainingDependencies.compute(childId, (key, value) -> value - 1);
            }
        }
        enqueueReadyNodes(
                plan,
                nodeResults,
                remainingDependencies,
                edgeStates,
                routed,
                scheduledNodeIds,
                readyNodes);
    }

    private void resolveOutgoingEdges(
            JobExecutionNode node,
            NodeExecuteResult result,
            JobExecutionPlan plan,
            Map<Long, EdgeActivation> edgeStates) {
        for (JobExecutionEdge edge : plan.outgoingEdgeMap().get(node.id())) {
            EdgeActivation activation;
            if (result.inactivePath()) {
                activation = EdgeActivation.INACTIVE;
            } else if (node.kind() == JobNodeKind.GATEWAY) {
                activation =
                        result.selectedEdgeIds().contains(edge.id())
                                ? EdgeActivation.ACTIVE
                                : EdgeActivation.INACTIVE;
            } else {
                activation = EdgeActivation.ACTIVE;
            }
            edgeStates.put(edge.id(), activation);
        }
    }

    private Map<Long, EdgeActivation> initializeEdgeStates(
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> nodeResults,
            Set<Long> inactiveNodeIds) {
        Map<Long, EdgeActivation> states = new HashMap<>();
        plan.edges().forEach(edge -> states.put(edge.id(), EdgeActivation.UNRESOLVED));
        for (JobExecutionNode node : plan.nodes()) {
            if (nodeResults.containsKey(node.id()) && node.kind() != JobNodeKind.GATEWAY) {
                EdgeActivation activation =
                        inactiveNodeIds.contains(node.id())
                                ? EdgeActivation.INACTIVE
                                : EdgeActivation.ACTIVE;
                plan.outgoingEdgeMap()
                        .get(node.id())
                        .forEach(edge -> states.put(edge.id(), activation));
            }
        }
        return states;
    }

    private Map<Long, InstanceStatus> validateAndCopyHistory(
            JobExecutionPlan plan, Map<Long, InstanceStatus> historyResults) {
        Map<Long, InstanceStatus> copy = new LinkedHashMap<>();
        if (historyResults == null) {
            return copy;
        }
        historyResults.forEach(
                (nodeId, status) -> {
                    if (!plan.nodeMap().containsKey(nodeId)
                            || plan.nodeMap().get(nodeId).kind() == JobNodeKind.GATEWAY
                            || status == null
                            || !status.isFinished()) {
                        throw systemError("Job DAG history result is invalid");
                    }
                    copy.put(nodeId, status);
                });
        return copy;
    }

    private Set<Long> validateInactiveHistory(
            Map<Long, InstanceStatus> nodeResults, Set<Long> inactiveHistoryNodeIds) {
        Set<Long> result =
                inactiveHistoryNodeIds == null
                        ? new HashSet<>()
                        : new HashSet<>(inactiveHistoryNodeIds);
        if (!nodeResults.keySet().containsAll(result)
                || result.stream().anyMatch(id -> nodeResults.get(id) != InstanceStatus.SKIPPED)) {
            throw systemError("Job DAG inactive history is invalid");
        }
        return result;
    }

    private Map<Long, Integer> remainingDependencies(
            JobExecutionPlan plan, Map<Long, InstanceStatus> nodeResults) {
        Map<Long, Integer> remaining = new HashMap<>();
        for (JobExecutionNode node : plan.nodes()) {
            int count =
                    (int)
                            plan.parentMap().get(node.id()).stream()
                                    .filter(parentId -> !nodeResults.containsKey(parentId))
                                    .count();
            remaining.put(node.id(), count);
        }
        return remaining;
    }

    private void enqueueReadyNodes(
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> nodeResults,
            Map<Long, Integer> remainingDependencies,
            Map<Long, EdgeActivation> edgeStates,
            boolean routed,
            Set<Long> scheduledNodeIds,
            Queue<JobExecutionNode> readyNodes) {
        for (JobExecutionNode node : plan.nodes()) {
            boolean ready =
                    routed
                            ? plan.incomingEdgeMap().get(node.id()).stream()
                                    .allMatch(
                                            edge ->
                                                    edgeStates.get(edge.id())
                                                            != EdgeActivation.UNRESOLVED)
                            : remainingDependencies.get(node.id()) == 0;
            if (!nodeResults.containsKey(node.id()) && ready && scheduledNodeIds.add(node.id())) {
                readyNodes.offer(node);
            }
        }
    }

    private boolean containsStopFailure(
            JobExecutionPlan plan, Map<Long, InstanceStatus> nodeResults) {
        return nodeResults.entrySet().stream()
                .anyMatch(
                        entry -> {
                            InstanceStatus status = entry.getValue();
                            JobExecutionNode node = plan.nodeMap().get(entry.getKey());
                            return (status == InstanceStatus.FAILED
                                            || status == InstanceStatus.TIMED_OUT)
                                    && node.failStrategy() == FailStrategy.STOP;
                        });
    }

    private void checkControl(TaskContext controlContext) {
        controlContext.checkStop();
        controlContext.checkPause();
        controlContext.checkInterrupted();
    }

    private InstanceStatus toInstanceStatus(NodeExecuteStatus status) {
        return switch (status) {
            case SUCCESS -> InstanceStatus.SUCCESS;
            case FAILED -> InstanceStatus.FAILED;
            case TIMED_OUT -> InstanceStatus.TIMED_OUT;
            case STOPPED -> InstanceStatus.STOPPED;
            case SKIPPED -> InstanceStatus.SKIPPED;
            case WAITING_EVENT ->
                    throw systemError("Suspended node cannot be converted to terminal status");
        };
    }

    private BusinessException systemError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }

    private enum EdgeActivation {
        UNRESOLVED,
        ACTIVE,
        INACTIVE
    }
}
