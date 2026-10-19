// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifielsc.ijob.core.etl.EtlMessageBindingStore;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.mq.MessageDelivery;
import com.ifielsc.ijob.core.resource.mq.MessageSourceOptions;
import com.ifielsc.ijob.core.resource.mq.MessageSourceSession;
import com.ifielsc.ijob.plugin.etl.EtlPipelineFactory;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.model.EtlResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EtlMessageRunner {
    private final ResourceResolver resources;
    private final EtlMessageBindingStore bindings;
    private final EtlPipelineFactory pipelines;
    private final ObjectMapper mapper;

    public EtlResult execute(EtlContext context) throws Exception {
        var config = context.getConfig();
        var source = config.getSource();
        var limits = config.getMessage();
        var task = context.getTaskContext();
        String token = UUID.randomUUID().toString();
        String fingerprint = fingerprint(context);
        EtlCheckpointSnapshot[] binding = {null};
        long waitDeadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (binding[0] == null) {
            context.checkControl();
            binding[0] = bindings.acquire(context.getInstanceId(), fingerprint, token);
            if (binding[0] == null) {
                if (System.nanoTime() >= waitDeadline)
                    throw new IllegalStateException(
                            "Message subscription concurrency wait expired");
                Thread.sleep(100);
            }
        }
        context.setWriteGuard(
                () -> binding[0] = bindings.verify(binding[0], context.getInstanceId(), token));
        ResourceClientLease<MessageSourceSession> lease = null;
        long started = System.nanoTime(),
                lastReceived = started,
                batchStarted = started,
                received = 0,
                bytes = 0,
                confirmed = 0;
        List<MessageDelivery> batch = new ArrayList<>();
        var positions = new java.util.LinkedHashMap<String, Object>();
        var decoder = new MessageJsonDecoder(config);
        String reason = "IDLE";
        task.putVariable("etl.receivedMessages", 0L);
        task.putVariable("etl.confirmedMessages", 0L);
        task.putVariable("etl.confirmationUnknown", false);
        task.putVariable("etl.parseFailures", 0L);
        task.logInfo("ETL bounded message consumption started, source=" + source.getType());
        try {
            while (true) {
                task.checkStop();
                task.checkInterrupted();
                boolean pausing = task.getHandle().isPauseRequested();
                long now = System.nanoTime();
                boolean countEnd = received >= limits.getMaxMessages();
                boolean timeEnd =
                        now - started >= Duration.ofSeconds(limits.getIntakeSeconds()).toNanos();
                boolean idleEnd =
                        now - lastReceived >= Duration.ofSeconds(limits.getIdleSeconds()).toNanos();
                boolean flush =
                        !batch.isEmpty()
                                && (pausing
                                        || countEnd
                                        || timeEnd
                                        || idleEnd
                                        || batch.size() >= limits.getBatchSize()
                                        || now - batchStarted
                                                >= Duration.ofMillis(
                                                                limits.getFlushIntervalMillis())
                                                        .toNanos());
                if (flush) {
                    context.setDeferPause(true);
                    try {
                        var records = new ArrayList<com.ifielsc.ijob.plugin.etl.model.DataRecord>();
                        long expandedBytes = 0;
                        for (var delivery : batch) {
                            try {
                                records.add(decoder.decode(delivery));
                            } catch (IllegalArgumentException exception) {
                                task.putVariable("etl.parseFailures", 1L);
                                throw exception;
                            }
                            expandedBytes += decoder.decodedBytes();
                            if (expandedBytes > limits.getMaxBufferBytes())
                                throw new IllegalArgumentException(
                                        "Decoded message batch limit exceeded");
                        }
                        pipelines.createRecords(config, records).execute(context);
                        task.checkStop();
                        context.getWriteGuard().run();
                        task.putVariable("etl.confirmationUnknown", true);
                        lease.getClient().confirm(List.copyOf(batch));
                        confirmed += batch.size();
                        task.putVariable("etl.confirmedMessages", confirmed);
                        task.putVariable("etl.confirmationUnknown", false);
                        if (source.getType()
                                == com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig.SourceType
                                        .KAFKA) {
                            for (var d : batch)
                                positions.put(
                                        String.valueOf(d.metadata().get("partition")),
                                        d.metadata().get("offset"));
                            task.putVariable(
                                    "etl.lastConfirmedOffsets",
                                    new java.util.LinkedHashMap<>(positions));
                        }
                        batch.clear();
                    } finally {
                        context.setDeferPause(false);
                    }
                }
                if (pausing) {
                    if (lease != null) {
                        lease.close();
                        task.unregisterResource(lease);
                        lease = null;
                    }
                    context.checkControl();
                    lastReceived = System.nanoTime();
                    continue;
                }
                if (countEnd || timeEnd || idleEnd) {
                    reason = countEnd ? "MAX_MESSAGES" : timeEnd ? "INTAKE_WINDOW" : "IDLE";
                    break;
                }
                if (lease == null) {
                    context.getWriteGuard().run();
                    boolean kafka =
                            source.getType()
                                    == com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig.SourceType
                                            .KAFKA;
                    lease =
                            resources.leaseMessageSource(
                                    source.getResourceId(),
                                    kafka ? ResourceType.KAFKA : ResourceType.RABBITMQ,
                                    new MessageSourceOptions(
                                            kafka ? source.getTopic() : source.getQueue(),
                                            source.getGroupId(),
                                            source.getInitialPosition(),
                                            limits.getBatchSize(),
                                            source.getPrefetch(),
                                            limits.getMaxMessageBytes(),
                                            limits.getMaxBufferBytes()));
                    task.registerResource(lease);
                    lastReceived = System.nanoTime();
                }
                MessageDelivery delivery = lease.getClient().poll(Duration.ofMillis(100));
                if (delivery != null) {
                    bytes = Math.addExact(bytes, delivery.body().length);
                    if (bytes > limits.getMaxTotalBytes())
                        throw new IllegalArgumentException("Message total byte limit exceeded");
                    if (batch.isEmpty()) batchStarted = System.nanoTime();
                    batch.add(delivery);
                    received++;
                    lastReceived = System.nanoTime();
                    task.putVariable("etl.receivedMessages", received);
                    task.putVariable("etl.messageBytes", bytes);
                    if (Boolean.TRUE.equals(delivery.metadata().get("redelivered"))) {
                        Object old = task.getVariable("etl.redeliveredMessages");
                        task.putVariable(
                                "etl.redeliveredMessages",
                                (old instanceof Number n ? n.longValue() : 0) + 1);
                    }
                }
            }
            task.putVariable("etl.endReason", reason);
            task.logInfo(
                    "ETL message batch completed, received="
                            + received
                            + ", confirmed="
                            + confirmed
                            + ", reason="
                            + reason);
            return EtlResult.success(context);
        } finally {
            context.setDeferPause(false);
            try {
                if (lease != null) {
                    lease.close();
                    task.unregisterResource(lease);
                }
            } finally {
                context.setWriteGuard(() -> {});
                bindings.release(binding[0], token);
            }
        }
    }

    private String fingerprint(EtlContext context) throws Exception {
        var c = context.getConfig();
        var s = c.getSource();
        var tree = mapper.createObjectNode();
        tree.put("sourceEndpoint", resources.endpointIdentity(s.getResourceId()));
        tree.put("targetEndpoint", resources.endpointIdentity(c.getTarget().getResourceId()));
        tree.put("resourceId", s.getResourceId());
        tree.put("type", s.getType().name());
        tree.put("topic", s.getTopic());
        tree.put("group", s.getGroupId());
        tree.put("queue", s.getQueue());
        tree.put("initialPosition", s.getInitialPosition());
        tree.set("target", mapper.valueToTree(c.getTarget()));
        tree.set("transforms", mapper.valueToTree(c.getTransforms()));
        tree.set("validations", mapper.valueToTree(c.getValidations()));
        tree.set("metadata", mapper.valueToTree(c.getMessage().getMetadataColumns()));
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(
                                        mapper.writeValueAsString(sorted(tree))
                                                .getBytes(StandardCharsets.UTF_8)));
    }

    private com.fasterxml.jackson.databind.JsonNode sorted(
            com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            var object = mapper.createObjectNode();
            var names = new java.util.TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) object.set(name, sorted(node.get(name)));
            return object;
        }
        if (node.isArray()) {
            var array = mapper.createArrayNode();
            node.forEach(item -> array.add(sorted(item)));
            return array;
        }
        return node;
    }
}
