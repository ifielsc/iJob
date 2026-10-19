package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.infra.mapper.runtime.InstanceLogMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class InstanceLogSnapshotIT {
    @TempDir Path directory;

    @Test
    void snapshotIncludesLateSmallIdsAndBoundsEachScopeWithStableTimestampTies() throws Exception {
        var db = new IdLogTestDatabase(directory.resolve("logs.db"));
        String number = db.oracle ? "NUMBER(19)" : "BIGINT";
        String time = db.oracle ? "TIMESTAMP(3)" : "TIMESTAMP";
        String level = db.oracle ? "log_level" : "level";
        db.execute(
                "CREATE TABLE ifj_instance (id "
                        + number
                        + " PRIMARY KEY, root_instance_id "
                        + number
                        + ")");
        db.execute(
                "CREATE TABLE ifj_instance_log (id "
                        + number
                        + " PRIMARY KEY, instance_id "
                        + number
                        + ", log_time "
                        + time
                        + ", "
                        + level
                        + " VARCHAR(20), content VARCHAR(100))");
        for (String values : List.of("(10,10)", "(11,10)", "(99,99)")) {
            db.execute("INSERT INTO ifj_instance (id,root_instance_id) VALUES " + values);
        }
        insert(db, 9007199254741993L, 10, "2026-10-08 10:00:00", level);
        try (var session = db.sessions.openSession()) {
            var mapper = session.getMapper(InstanceLogMapper.class);
            assertEquals(9007199254741993L, mapper.selectLatest(10L, false, 3).getFirst().getId());
        }
        insert(db, 20, 10, "2026-10-08 10:00:01", level);
        insert(db, 21, 11, "2026-10-08 10:00:01", level);
        insert(db, 22, 11, "2026-10-08 10:00:01", level);
        insert(db, 99999, 99, "2026-10-08 11:00:00", level);
        try (var session = db.sessions.openSession()) {
            var mapper = session.getMapper(InstanceLogMapper.class);
            assertEquals(List.of(), mapper.selectAfterId(10L, 9007199254741993L, 10));
            assertEquals(List.of(20L, 9007199254741993L), ids(mapper.selectLatest(10L, false, 10)));
            assertEquals(List.of(22L, 21L, 20L), ids(mapper.selectLatest(10L, true, 3)));
            assertEquals(List.of(22L), ids(mapper.selectLatest(11L, false, 1)));
        }
    }

    private void insert(IdLogTestDatabase db, long id, long instance, String time, String level)
            throws Exception {
        String timestamp = db.oracle ? "TIMESTAMP '" + time + "'" : "'" + time + "'";
        db.execute(
                "INSERT INTO ifj_instance_log (id,instance_id,log_time,"
                        + level
                        + ",content) VALUES ("
                        + id
                        + ","
                        + instance
                        + ","
                        + timestamp
                        + ",'INFO','fixture')");
    }

    private List<Long> ids(List<com.ifielsc.ijob.infra.entity.runtime.InstanceLog> rows) {
        return rows.stream().map(r -> r.getId()).toList();
    }
}
