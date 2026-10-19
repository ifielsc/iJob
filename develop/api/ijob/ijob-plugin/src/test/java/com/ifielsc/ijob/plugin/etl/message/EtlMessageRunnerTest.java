// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.message;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.resource.mq.MessageDelivery;
import com.ifielsc.ijob.core.resource.mq.MessageSourceSession;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

class EtlMessageRunnerTest extends MessageTestSupport {
    EtlMessageRunnerTest() throws Exception {}

    class Session implements MessageSourceSession {
        final Queue<MessageDelivery> messages = new ArrayDeque<>();
        volatile int confirmed;
        volatile boolean closed;
        boolean failConfirm;
        Runnable onPoll = () -> {};

        Session(String... json) {
            for (var value : json) messages.add(delivery(value));
        }

        public MessageDelivery poll(Duration duration) {
            onPoll.run();
            return messages.poll();
        }

        public void confirm(List<MessageDelivery> batch) {
            assertTrue(
                    assertDoesNotThrow(() -> count()) > 0,
                    "Confirmation must observe a committed transaction on another connection");
            if (failConfirm)
                throw new IllegalStateException("Simulated lost confirmation response");
            confirmed += batch.size();
        }

        public void close() {
            closed = true;
        }
    }

    @Test
    void confirmsAfterCommitAndPreservesRepeatedKeyOrder() throws Exception {
        var session = new Session("{\"id\":1,\"value\":\"old\"}", "{\"id\":1,\"value\":\"new\"}");
        var context = context();
        run(() -> session, context);
        assertEquals("new", value());
        assertEquals(1, count());
        assertEquals(2, session.confirmed);
        assertTrue(session.closed);
        assertEquals("MAX_MESSAGES", context.getTaskContext().getVariable("etl.endReason"));
        assertEquals(1, releases);
    }

    @Test
    void confirmationFailureReplaysWithoutDuplicateRows() throws Exception {
        var first = new Session("{\"id\":1,\"value\":\"old\"}", "{\"id\":1,\"value\":\"new\"}");
        first.failConfirm = true;
        var context = context();
        assertThrows(IllegalStateException.class, () -> run(() -> first, context));
        assertEquals(true, context.getTaskContext().getVariable("etl.confirmationUnknown"));
        assertEquals(1, count());
        var replay = new Session("{\"id\":1,\"value\":\"old\"}", "{\"id\":1,\"value\":\"new\"}");
        run(() -> replay, context());
        assertEquals("new", value());
        assertEquals(1, count());
        assertEquals(2, replay.confirmed);
    }

    @Test
    void malformedMessagePreventsEntireBatchWriteAndConfirmation() throws Exception {
        var session = new Session("{\"id\":1,\"value\":\"ok\"}", "private business text");
        var error =
                assertThrows(IllegalArgumentException.class, () -> run(() -> session, context()));
        assertFalse(error.toString().contains("private business"));
        assertNull(error.getCause());
        assertEquals(0, count());
        assertEquals(0, session.confirmed);
        assertTrue(session.closed);
    }

    @Test
    void databaseFailureRollsBackEarlierRowsOfSameBatch() throws Exception {
        var session = new Session("{\"id\":1,\"value\":\"ok\"}", "{\"id\":2,\"value\":null}");
        assertThrows(IllegalStateException.class, () -> run(() -> session, context()));
        assertEquals(0, count());
        assertEquals(0, session.confirmed);
    }

    @Test
    void insertOnlyKeepsFirstValueOnReplay() throws Exception {
        config.getTarget().setMode(EtlTaskConfig.LoadMode.INSERT_ONLY);
        var session =
                new Session("{\"id\":1,\"value\":\"first\"}", "{\"id\":1,\"value\":\"later\"}");
        run(() -> session, context());
        assertEquals("first", value());
        assertEquals(2, session.confirmed);
    }

    @Test
    void rejectsTargetWithoutUniqueLogicalKey() throws Exception {
        try (var c = database.getConnection()) {
            c.createStatement().execute("ALTER TABLE ORDERS DROP PRIMARY KEY");
        }
        var session = new Session("{\"id\":1,\"value\":\"one\"}", "{\"id\":2,\"value\":\"two\"}");
        assertThrows(IllegalStateException.class, () -> run(() -> session, context()));
        assertEquals(0, session.confirmed);
        assertEquals(0, count());
    }

    @Test
    void idleBoundaryDrainsPartialBatch() throws Exception {
        var session = new Session("{\"id\":1,\"value\":\"one\"}");
        var context = context();
        run(() -> session, context);
        assertEquals(1, session.confirmed);
        assertEquals("IDLE", context.getTaskContext().getVariable("etl.endReason"));
    }

    @Test
    void stopBeforeFlushLeavesMessagesUnconfirmed() throws Exception {
        var context = context();
        var session = new Session("{\"id\":1,\"value\":\"one\"}");
        session.onPoll = () -> context.getTaskContext().getHandle().requestStop();
        assertThrows(
                com.ifielsc.ijob.common.exception.StopException.class,
                () -> run(() -> session, context));
        assertEquals(0, count());
        assertEquals(0, session.confirmed);
        assertTrue(session.closed);
    }

    @Test
    void pauseDrainsBatchAndClosesChannelBeforeWaiting() throws Exception {
        var context = context();
        var session = new Session("{\"id\":1,\"value\":\"one\"}");
        session.onPoll = () -> context.getTaskContext().getHandle().requestPause();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future =
                    executor.submit(
                            () -> {
                                run(() -> session, context);
                                return null;
                            });
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!session.closed && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.closed);
            assertEquals(1, session.confirmed);
            assertFalse(future.isDone());
            context.getTaskContext().getHandle().requestStop();
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> future.get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            context.getTaskContext().getHandle().requestStop();
            executor.shutdownNow();
        }
    }
}
