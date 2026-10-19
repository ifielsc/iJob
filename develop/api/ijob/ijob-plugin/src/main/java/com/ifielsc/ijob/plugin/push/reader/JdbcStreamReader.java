package com.ifielsc.ijob.plugin.push.reader;

import com.ifielsc.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifielsc.ijob.plugin.common.jdbc.JdbcStreaming;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

public class JdbcStreamReader implements DataReader {
    private final CancelableJdbcResources resources = new CancelableJdbcResources();
    private final ResultSet resultSet;
    private final List<String> columnLabels;

    public JdbcStreamReader(DataSource dataSource, String sql, int fetchSize) throws Exception {
        this(dataSource, sql, fetchSize, null);
    }

    public JdbcStreamReader(
            DataSource dataSource, String sql, int fetchSize, Consumer<AutoCloseable> registrar)
            throws Exception {
        try {
            Connection connection = dataSource.getConnection();
            resources.attach(connection);
            if (registrar != null) {
                registrar.accept(resources);
            }
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            resources.attach(statement);
            JdbcStreaming.configureFetch(statement, connection.getMetaData(), fetchSize);
            ResultSet openedResultSet = statement.executeQuery();
            resources.attach(openedResultSet);
            resultSet = openedResultSet;
            ResultSetMetaData metadata = openedResultSet.getMetaData();
            int count = metadata.getColumnCount();
            java.util.ArrayList<String> labels = new java.util.ArrayList<>(count);
            for (int index = 1; index <= count; index++) {
                labels.add(metadata.getColumnLabel(index));
            }
            columnLabels = List.copyOf(labels);
        } catch (Exception e) {
            resources.close();
            throw e;
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return resultSet.next();
        } catch (java.sql.SQLException e) {
            throw new org.springframework.jdbc.UncategorizedSQLException(
                    "Read push source", null, e);
        }
    }

    @Override
    public Map<String, Object> next() {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 0; index < columnLabels.size(); index++) {
                row.put(columnLabels.get(index), resultSet.getObject(index + 1));
            }
            return row;
        } catch (java.sql.SQLException e) {
            throw new org.springframework.jdbc.UncategorizedSQLException(
                    "Read push source", null, e);
        }
    }

    @Override
    public void close() {
        resources.close();
    }
}
