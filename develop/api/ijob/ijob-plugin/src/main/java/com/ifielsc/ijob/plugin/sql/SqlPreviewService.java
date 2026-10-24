// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.plugin.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.common.diagnostic.SafeDiagnostic;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.resource.SqlPreviewDTO;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.SqlPreview;
import com.ifielsc.ijob.plugin.etl.sql.NamedSql;
import com.ifielsc.ijob.plugin.support.sql.ReadOnlySqlGuard;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Interactive reads own their connections and never enter task execution or checkpoint state. */
@Service
@Slf4j
public class SqlPreviewService implements SqlPreview {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int CELL_BYTES = 16 * 1024;
    private static final Pattern UNSAFE =
            Pattern.compile(
                    "(?i)/\\*!|/\\*\\+|\\b(load_file|into|outfile|dumpfile|nextval|setval|get_lock|release_lock|dblink|lo_import|lo_export|xp_[a-z_]+|sys_eval|sys_exec)\\b");
    private final ResourceResolver resources;
    private final VariableResolver variables;
    private final ClusterStateStore store;
    private final ObjectMapper json;
    private final Semaphore slots = new Semaphore(4);
    private final Set<Active> active = ConcurrentHashMap.newKeySet();
    private final ScheduledThreadPoolExecutor watcher =
            new ScheduledThreadPoolExecutor(2, r -> new Thread(r, "ijob-sql-preview-watch"));
    private final ThreadPoolExecutor canceller =
            new ThreadPoolExecutor(
                    2,
                    2,
                    0,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(8),
                    r -> new Thread(r, "ijob-sql-preview-cancel"),
                    new ThreadPoolExecutor.AbortPolicy());

    public SqlPreviewService(
            ResourceResolver resources,
            VariableResolver variables,
            ClusterStateStore store,
            ObjectMapper json) {
        this.resources = resources;
        this.variables = variables;
        this.store = store;
        this.json = json;
        watcher.setRemoveOnCancelPolicy(true);
    }

    @Override
    public SqlPreviewDTO.Result query(SqlPreviewDTO.Request request, String actor) {
        String trace = UUID.randomUUID().toString();
        long start = System.nanoTime();
        if (request == null
                || request.resourceId() == null
                || request.resourceId() <= 0
                || request.sql() == null
                || request.sql().length() > 65536
                || !Set.of("SQL", "PUSH", "ETL", "DATA_CHECK")
                        .contains(String.valueOf(request.mode()))
                || (request.maxRows() != null
                        && (request.maxRows() < 1 || request.maxRows() > 1000)))
            throw new BusinessException(ResultCode.PARAM_ERROR);
        String key = key(request.requestId(), actor);
        Map<String, Object> vars = request.variables() == null ? Map.of() : request.variables();
        Map<String, Object> params = request.parameters() == null ? Map.of() : request.parameters();
        validateValues(vars);
        validateValues(params);
        String sql = variables.resolve(request.sql(), vars);
        if (sql.contains("${")) return result("PARAMETERS", start, trace);
        if (sql.length() > 65536
                || !ReadOnlySqlGuard.isReadQuery(sql)
                || UNSAFE.matcher(sql).find()) return result("READ_ONLY", start, trace);
        NamedSql named;
        try {
            named =
                    "ETL".equals(request.mode())
                            ? NamedSql.parse(sql, variables.resolveObjectMap(params, vars))
                            : new NamedSql(sql, List.of());
        } catch (IllegalArgumentException e) {
            return result("PARAMETERS", start, trace);
        }
        if (!slots.tryAcquire()) return result("BUSY", start, trace);
        Active operation = new Active();
        active.add(operation);
        String owner = UUID.randomUUID().toString();
        boolean registered = false;
        boolean queryStarted = false;
        try {
            if (!owner.equals(store.initializeOrRead(key, owner)))
                return result("BUSY", start, trace);
            registered = true;
            store.updateTimeout(key, 120);
            var monitor =
                    watcher.scheduleWithFixedDelay(
                            () -> {
                                if (System.nanoTime() - start >= TimeUnit.SECONDS.toNanos(30)) {
                                    operation.stop("TIMEOUT");
                                } else {
                                    try {
                                        if (store.get(key + ":cancel") != null)
                                            operation.stop("CANCELLED");
                                    } catch (RuntimeException e) {
                                        operation.stop("UNAVAILABLE");
                                    }
                                }
                            },
                            0,
                            200,
                            TimeUnit.MILLISECONDS);
            try (Connection connection =
                    resources.getDataSource(request.resourceId()).getConnection()) {
                operation.connection = connection;
                String vendor = connection.getMetaData().getDatabaseProductName();
                boolean sqlite = "SQLite".equalsIgnoreCase(vendor);
                if (!Set.of("SQLite", "MySQL", "MariaDB", "PostgreSQL", "Oracle").contains(vendor))
                    return result("UNSUPPORTED", start, trace);
                try {
                    if (sqlite) {
                        try (Statement setup = connection.createStatement()) {
                            setup.execute("PRAGMA query_only=ON");
                        }
                    } else connection.setReadOnly(true);
                    connection.setAutoCommit(false);
                    if (!sqlite) {
                        try (Statement setup = connection.createStatement()) {
                            setup.setQueryTimeout(5);
                            setup.execute("SET TRANSACTION READ ONLY");
                        }
                    }
                    if (operation.stopped != null) return result(operation.stopped, start, trace);
                    queryStarted = true;
                    try (PreparedStatement statement = connection.prepareStatement(named.sql())) {
                        operation.statement = statement;
                        int maxRows = request.maxRows() == null ? 200 : request.maxRows();
                        statement.setMaxRows(maxRows + 1);
                        statement.setQueryTimeout(30);
                        statement.setFetchSize(Math.min(200, maxRows));
                        for (int i = 0; i < named.values().size(); i++)
                            statement.setObject(i + 1, named.values().get(i));
                        if (operation.stopped != null)
                            return result(operation.stopped, start, trace);
                        try (ResultSet rs = statement.executeQuery()) {
                            var metadata = rs.getMetaData();
                            int count = metadata.getColumnCount();
                            if (count > 256) return result("COLUMN_LIMIT", start, trace);
                            List<SqlPreviewDTO.Column> columns = new ArrayList<>();
                            for (int i = 1; i <= count; i++)
                                columns.add(
                                        new SqlPreviewDTO.Column(
                                                bounded(metadata.getColumnLabel(i), 256),
                                                bounded(metadata.getColumnTypeName(i), 64)));
                            List<List<String>> rows = new ArrayList<>();
                            int bytes = json.writeValueAsBytes(columns).length;
                            String truncated = "";
                            while (rs.next()) {
                                if (operation.stopped != null)
                                    return result(operation.stopped, start, trace);
                                if (rows.size() >= maxRows) {
                                    truncated = "ROWS";
                                    break;
                                }
                                List<String> row = new ArrayList<>();
                                for (int i = 1; i <= count; i++) {
                                    int type = metadata.getColumnType(i);
                                    if (type == Types.BLOB
                                            || type == Types.BINARY
                                            || type == Types.VARBINARY
                                            || type == Types.LONGVARBINARY) {
                                        try (var binary = rs.getBinaryStream(i)) {
                                            row.add(binary == null ? null : "[binary]");
                                        }
                                    } else if (scalar(type)) {
                                        // MySQL's ReaderValueFactory rejects numeric/date values.
                                        // String conversion preserves their exact JDBC
                                        // representation.
                                        String cell = rs.getString(i);
                                        if (cell != null
                                                && cell.getBytes(StandardCharsets.UTF_8).length
                                                        > CELL_BYTES) {
                                            cell = truncateCell(cell);
                                            truncated = "CELL";
                                        }
                                        row.add(cell);
                                    } else {
                                        try (Reader reader = rs.getCharacterStream(i)) {
                                            if (reader == null) {
                                                row.add(null);
                                                continue;
                                            }
                                            StringBuilder value = new StringBuilder();
                                            char[] buffer = new char[1024];
                                            int read;
                                            while (value.length() <= CELL_BYTES
                                                    && (read =
                                                                    reader.read(
                                                                            buffer,
                                                                            0,
                                                                            Math.min(
                                                                                    buffer.length,
                                                                                    CELL_BYTES
                                                                                            + 1
                                                                                            - value
                                                                                                    .length())))
                                                            > 0) value.append(buffer, 0, read);
                                            String cell = value.toString();
                                            if (cell.getBytes(StandardCharsets.UTF_8).length
                                                    > CELL_BYTES) {
                                                cell = truncateCell(cell);
                                                truncated = "CELL";
                                            }
                                            row.add(cell);
                                        }
                                    }
                                }
                                bytes += json.writeValueAsBytes(row).length + 1;
                                if (bytes > MAX_BYTES - 2048) {
                                    truncated = "BYTES";
                                    break;
                                }
                                rows.add(row);
                            }
                            return new SqlPreviewDTO.Result(
                                    "SUCCESS",
                                    columns,
                                    rows,
                                    elapsed(start),
                                    truncated,
                                    null,
                                    null,
                                    trace,
                                    null,
                                    null);
                        }
                    }
                } finally {
                    operation.finish();
                    operation.statement = null;
                    if (!connection.isClosed()) {
                        connection.rollback();
                        connection.setAutoCommit(true);
                        if (sqlite) {
                            try (Statement reset = connection.createStatement()) {
                                reset.execute("PRAGMA query_only=OFF");
                            }
                        } else connection.setReadOnly(false);
                    }
                }
            } finally {
                operation.finish();
                monitor.cancel(false);
                operation.connection = null;
            }
        } catch (Exception e) {
            log.warn("SQL preview failed traceId={}", trace, SafeDiagnostic.sanitized(e));
            String state = e instanceof SQLException se ? se.getSQLState() : null;
            if (state != null && !state.matches("[A-Z0-9]{5}")) state = null;
            Integer position = null;
            if (e instanceof SQLException && e.getMessage() != null) {
                var matcher = Pattern.compile("(?i)Position: (\\d{1,6})").matcher(e.getMessage());
                if (matcher.find()) position = Integer.valueOf(matcher.group(1));
            }
            var diagnosticValues = new ArrayList<Object>(vars.values());
            diagnosticValues.addAll(params.values());
            diagnosticValues.addAll(named.values());
            boolean showDiagnostic =
                    queryStarted && e instanceof SQLException && operation.stopped == null;
            return new SqlPreviewDTO.Result(
                    operation.stopped != null
                            ? operation.stopped
                            : e instanceof java.sql.SQLTimeoutException
                                    ? "TIMEOUT"
                                    : state != null && state.startsWith("42")
                                            ? "SQL_SYNTAX"
                                            : "QUERY_FAILED",
                    List.of(),
                    List.of(),
                    elapsed(start),
                    "",
                    state,
                    position,
                    trace,
                    showDiagnostic ? ((SQLException) e).getErrorCode() : null,
                    showDiagnostic
                            ? SqlPreviewDiagnostic.message(e.getMessage(), sql, diagnosticValues)
                            : null);
        } finally {
            active.remove(operation);
            slots.release();
            if (registered) {
                try {
                    store.delete(key);
                    store.delete(key + ":cancel");
                } catch (RuntimeException e) {
                    log.warn("SQL preview metadata expires by TTL traceId={}", trace);
                }
            }
        }
    }

    private void validateValues(Map<String, Object> values) {
        if (values.size() > 100) throw new BusinessException(ResultCode.PARAM_ERROR);
        int size = 0;
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z_][A-Za-z0-9_.]{0,127}"))
                throw new BusinessException(ResultCode.PARAM_ERROR);
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String
                            || value instanceof Number
                            || value instanceof Boolean))
                throw new BusinessException(ResultCode.PARAM_ERROR);
            size += String.valueOf(value).length();
            if (size > 65536) throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    @Override
    public void cancel(String requestId, String actor) {
        store.set(key(requestId, actor) + ":cancel", "cancel", 120);
    }

    private String key(String requestId, String actor) {
        if (requestId == null || !requestId.matches("[a-f0-9-]{36}") || actor == null)
            throw new BusinessException(ResultCode.PARAM_ERROR);
        try {
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(actor.getBytes(StandardCharsets.UTF_8)));
            return "ijob:preview:" + hash + ":" + requestId;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }

    private static String truncateCell(String value) {
        int end = 0, bytes = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int length =
                    codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (bytes + length > CELL_BYTES - 3) break;
            bytes += length;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end) + "…";
    }

    private static boolean scalar(int type) {
        return switch (type) {
            case Types.NULL,
                            Types.BIT,
                            Types.BOOLEAN,
                            Types.TINYINT,
                            Types.SMALLINT,
                            Types.INTEGER,
                            Types.BIGINT,
                            Types.FLOAT,
                            Types.REAL,
                            Types.DOUBLE,
                            Types.NUMERIC,
                            Types.DECIMAL,
                            Types.DATE,
                            Types.TIME,
                            Types.TIMESTAMP,
                            Types.TIME_WITH_TIMEZONE,
                            Types.TIMESTAMP_WITH_TIMEZONE ->
                    true;
            default -> false;
        };
    }

    private static SqlPreviewDTO.Result result(String status, long start, String trace) {
        return new SqlPreviewDTO.Result(
                status, List.of(), List.of(), elapsed(start), "", null, null, trace, null, null);
    }

    private final class Active {
        volatile Connection connection;
        volatile Statement statement;
        volatile String stopped;
        boolean finished;
        final AtomicBoolean cancellation = new AtomicBoolean();

        synchronized void finish() {
            finished = true;
        }

        void stop(String reason) {
            if (!cancellation.compareAndSet(false, true)) return;
            stopped = reason;
            try {
                canceller.execute(
                        () -> {
                            synchronized (this) {
                                if (finished) return;
                                try {
                                    if (statement != null) statement.cancel();
                                } catch (SQLException ignored) {
                                    /* Abort below. */
                                }
                                try {
                                    if (connection != null) connection.abort(Runnable::run);
                                } catch (SQLException ignored) {
                                    /* Query timeout remains enforced by the driver. */
                                }
                            }
                        });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // Bounded cancellation work must never block the watchdog; JDBC timeout remains
                // active.
            }
        }
    }

    @PreDestroy
    public void close() {
        active.forEach(operation -> operation.stop("CANCELLED"));
        watcher.shutdownNow();
        canceller.shutdown();
    }
}
