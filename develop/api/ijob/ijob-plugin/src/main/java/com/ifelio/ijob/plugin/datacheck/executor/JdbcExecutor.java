package com.ifelio.ijob.plugin.datacheck.executor;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class JdbcExecutor {
    private final ResourceResolver resourceResolver;

    public List<Map<String, Object>> query(
            Long resourceId, String sql, int timeoutSeconds, TaskContext context) {
        return query(
                resourceId,
                sql,
                timeoutSeconds,
                context,
                resultSet -> new ResultSetExtractor().extractData(resultSet));
    }

    public Object queryForObject(
            Long resourceId, String sql, int timeoutSeconds, TaskContext context) {
        return query(
                resourceId,
                sql,
                timeoutSeconds,
                context,
                resultSet -> resultSet.next() ? resultSet.getObject(1) : null);
    }

    public long queryCount(Long resourceId, String sql, int timeoutSeconds, TaskContext context) {
        Object value = queryForObject(resourceId, sql, timeoutSeconds, context);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public boolean exists(Long resourceId, String sql, int timeoutSeconds, TaskContext context) {
        return query(resourceId, sql, timeoutSeconds, context, ResultSet::next);
    }

    private <T> T query(
            Long resourceId,
            String sql,
            int timeoutSeconds,
            TaskContext context,
            ResultReader<T> reader) {
        CancelableJdbcResources resources = new CancelableJdbcResources();
        try {
            DataSource dataSource = resourceResolver.getDataSource(resourceId);
            context.checkExecution();
            Connection connection = dataSource.getConnection();
            resources.attach(connection);
            context.registerResource(resources);
            context.checkStop();
            context.checkInterrupted();
            connection.setReadOnly(true);
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            resources.attach(statement);
            statement.setQueryTimeout(timeoutSeconds);
            context.checkStop();
            context.checkInterrupted();
            ResultSet resultSet = statement.executeQuery();
            resources.attach(resultSet);
            context.checkStop();
            context.checkInterrupted();
            return reader.read(resultSet);
        } catch (SQLException e) {
            throw new IllegalStateException("data check query failed", e);
        } finally {
            resources.close();
        }
    }

    @FunctionalInterface
    private interface ResultReader<T> {
        T read(ResultSet resultSet) throws SQLException;
    }
}
