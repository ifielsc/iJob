package com.ifelio.ijob.plugin.etl.extractor.database;

import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifelio.ijob.plugin.common.jdbc.JdbcColumnValues;
import com.ifelio.ijob.plugin.common.jdbc.JdbcStreaming;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.reader.DataReader;
import com.ifelio.ijob.plugin.etl.sql.NamedSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

public final class JdbcDataReader implements DataReader {
    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;
    private final CancelableJdbcResources resources;
    private final String[] columns;
    private final int[] columnTypes;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean fetched;
    private boolean hasNext;

    private JdbcDataReader(
            Connection connection,
            PreparedStatement statement,
            ResultSet resultSet,
            CancelableJdbcResources resources)
            throws SQLException {
        this.connection = connection;
        this.statement = statement;
        this.resultSet = resultSet;
        this.resources = resources;
        ResultSetMetaData metadata = resultSet.getMetaData();
        columns = new String[metadata.getColumnCount()];
        columnTypes = new int[columns.length];
        Set<String> labels = new HashSet<>();
        for (int index = 0; index < columns.length; index++) {
            columns[index] = metadata.getColumnLabel(index + 1);
            columnTypes[index] = metadata.getColumnType(index + 1);
            if (columns[index] == null || columns[index].isBlank() || !labels.add(columns[index])) {
                throw new IllegalArgumentException(
                        "ETL SQL result columns must be non-blank and unique; use explicit"
                                + " aliases");
            }
        }
    }

    public static JdbcDataReader open(
            DataSource dataSource,
            EtlTaskConfig.SourceConfig config,
            EtlContext context,
            Map<String, Object> parameters) {
        return open(dataSource, config, context, parameters, true);
    }

    public static JdbcDataReader open(
            DataSource dataSource,
            EtlTaskConfig.SourceConfig config,
            EtlContext context,
            Map<String, Object> parameters,
            boolean prepareSource) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        CancelableJdbcResources resources = new CancelableJdbcResources();
        try {
            context.getTaskContext().registerResource(resources);
            connection = dataSource.getConnection();
            resources.attach(connection);
            if (prepareSource) {
                executeBeforeSql(connection, config, context, resources);
            }
            connection.setAutoCommit(false);
            String resolvedSql = context.getTaskContext().resolve(config.getSql());
            NamedSql namedSql = NamedSql.parse(resolvedSql, parameters);
            statement =
                    connection.prepareStatement(
                            namedSql.sql(),
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY);
            resources.attach(statement);
            JdbcStreaming.configureFetch(
                    statement, connection.getMetaData(), config.getFetchSize());
            statement.setQueryTimeout(context.getConfig().getExecution().getQueryTimeoutSeconds());
            for (int index = 0; index < namedSql.values().size(); index++) {
                statement.setObject(index + 1, namedSql.values().get(index));
            }
            resultSet = statement.executeQuery();
            resources.attach(resultSet);
            return new JdbcDataReader(connection, statement, resultSet, resources);
        } catch (Exception exception) {
            resources.close();
            throw new IllegalStateException("ETL database source could not be opened", exception);
        }
    }

    private static void executeBeforeSql(
            Connection connection,
            EtlTaskConfig.SourceConfig config,
            EtlContext context,
            CancelableJdbcResources resources)
            throws SQLException {
        for (String sql : config.getBeforeSql()) {
            context.checkControl();
            try (Statement statement = connection.createStatement()) {
                resources.attach(statement);
                statement.setQueryTimeout(
                        context.getConfig().getExecution().getQueryTimeoutSeconds());
                statement.execute(context.getTaskContext().resolve(sql));
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (fetched) {
            return hasNext;
        }
        try {
            hasNext = resultSet.next();
            fetched = true;
            return hasNext;
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL database source read failed", exception);
        }
    }

    @Override
    public DataRecord next() {
        if (!hasNext()) {
            return null;
        }
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < columns.length; index++) {
                values.put(
                        columns[index],
                        JdbcColumnValues.read(resultSet, index + 1, columnTypes[index]));
            }
            fetched = false;
            return new DataRecord(values);
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL database row mapping failed", exception);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        resources.close();
    }
}
