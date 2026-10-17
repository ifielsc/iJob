package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.infra.coordination.id.DatabaseIdSegmentProvider;
import com.ifelio.ijob.infra.mapper.coordination.IdSegmentMapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class OracleIdSegmentConcurrencyIT {
    @Test
    void competingConnectionsWaitThenReserveDisjointRangesAndRollbackReleasesLock()
            throws Exception {
        var db = new IdLogTestDatabase(Path.of("unused.db"));
        assertTrue(db.oracle, "An explicitly configured isolated Oracle schema is required");
        db.execute(
                "CREATE TABLE ifj_id_segment (segment_key VARCHAR2(64) PRIMARY KEY, next_value"
                        + " NUMBER(19), segment_size NUMBER(10), update_time TIMESTAMP(3))");
        db.execute("INSERT INTO ifj_id_segment VALUES ('race',1,100,SYSTIMESTAMP)");
        try (var first = db.sessions.openSession(false);
                var executor = Executors.newSingleThreadExecutor()) {
            var mapper = first.getMapper(IdSegmentMapper.class);
            assertEquals(1L, mapper.selectForUpdate("race").getNextValue());
            var started = new CountDownLatch(1);
            var second =
                    executor.submit(
                            () -> {
                                try (var session = db.sessions.openSession(false)) {
                                    var next = session.getMapper(IdSegmentMapper.class);
                                    started.countDown();
                                    next.lockExisting("race");
                                    var state = next.selectForUpdate("race");
                                    assertEquals(
                                            1,
                                            next.advance(
                                                    "race",
                                                    state.getNextValue(),
                                                    state.getNextValue() + 100,
                                                    100));
                                    session.commit();
                                    return state.getNextValue();
                                }
                            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            try {
                assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
                assertEquals(1, mapper.advance("race", 1, 101, 100));
                first.commit();
            } finally {
                first.rollback();
            }
            assertEquals(101L, second.get(10, TimeUnit.SECONDS));
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            var values = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(
                        executor.submit(
                                () -> {
                                    for (int iteration = 0; iteration < 20; iteration++) {
                                        try (var session = db.sessions.openSession(false)) {
                                            var range =
                                                    new DatabaseIdSegmentProvider(
                                                                    session.getMapper(
                                                                            IdSegmentMapper.class))
                                                            .reserve("race", 100);
                                            session.commit();
                                            values.add(range.startInclusive());
                                        }
                                    }
                                }));
            }
            for (var task : tasks) task.get(30, TimeUnit.SECONDS);
            assertEquals(80, values.size());
            assertEquals(80, new HashSet<>(values).size());
        }
        try (var session = db.sessions.openSession(false)) {
            new DatabaseIdSegmentProvider(session.getMapper(IdSegmentMapper.class))
                    .reserve("race", 100);
            session.rollback();
        }
        try (var session = db.sessions.openSession(false)) {
            assertEquals(
                    8201L,
                    session.getMapper(IdSegmentMapper.class)
                            .selectForUpdate("race")
                            .getNextValue());
            session.commit();
        }
    }
}
