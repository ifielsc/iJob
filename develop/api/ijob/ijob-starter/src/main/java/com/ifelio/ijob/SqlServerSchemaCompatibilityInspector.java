package com.ifelio.ijob;

import com.ifelio.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifelio.ijob.framework.database.schema.SchemaCompatibilityInspector;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

@Component
public class SqlServerSchemaCompatibilityInspector implements SchemaCompatibilityInspector {
    static final int REQUIRED_SCHEMA_VERSION = 38;

    private final DataSource dataSource;

    public SqlServerSchemaCompatibilityInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.SQLSERVER;
    }

    @Override
    public void verify() {
        String sql =
                "SELECT CASE WHEN (SELECT COUNT(*) FROM dbo.ifj_schema_capability WHERE enabled = 1"
                    + " AND ((capability_key = 'platform-schema' AND capability_version >= ?) OR"
                    + " (capability_key = 'event-wait-operator-relation' AND capability_version >="
                    + " 1) OR (capability_key = 'stable-id-segment-seed' AND capability_version >="
                    + " 1) OR (capability_key = 'execution-group-routing' AND capability_version >="
                    + " 1) OR (capability_key = 'conditional-dag' AND capability_version >= 1) OR"
                    + " (capability_key = 'execution-target-routing' AND capability_version >= 1)))"
                    + " = 6 AND EXISTS (SELECT 1 FROM dbo.ifj_schema_capability WHERE enabled = 1"
                    + " AND capability_key = 'composite-task-concurrency' AND capability_version >="
                    + " 1) AND EXISTS (SELECT 1 FROM dbo.ifj_schema_capability WHERE enabled = 1"
                    + " AND capability_key = 'schedule-misfire-policy' AND capability_version >= 1)"
                    + " AND EXISTS (SELECT 1 FROM dbo.ifj_schema_capability WHERE enabled = 1 AND"
                    + " capability_key = 'runtime-history-v2' AND capability_version >= 1) AND"
                    + " EXISTS (SELECT 1 FROM dbo.ifj_schema_history WHERE version = '1' AND"
                    + " success = 1) AND (SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON"
                    + " t.object_id = c.object_id JOIN sys.schemas s ON s.schema_id = t.schema_id"
                    + " WHERE s.name = SCHEMA_NAME() AND ((t.name = 'ifj_schema_capability' AND"
                    + " c.name = 'capability_key') OR (t.name = 'ifj_event_wait_operator' AND"
                    + " c.name = 'operator_id') OR (t.name = 'ifj_id_segment' AND c.name ="
                    + " 'next_value') OR (t.name = 'ifj_runtime_command' AND c.name ="
                    + " 'processing_fence') OR (t.name = 'ifj_trigger_work_item' AND c.name ="
                    + " 'fence_token') OR (t.name = 'ifj_app_run' AND c.name ="
                    + " 'runtime_protocol_version') OR (t.name = 'ifj_app_run' AND c.name ="
                    + " 'worker_task_ids') OR (t.name = 'ifj_app_run' AND c.name ="
                    + " 'worker_job_ids') OR (t.name = 'ifj_task_definition_group' AND c.name ="
                    + " 'group_code') OR (t.name = 'ifj_job_definition_group' AND c.name ="
                    + " 'group_code') OR (t.name = 'ifj_job_definition_node' AND c.name ="
                    + " 'node_kind') OR (t.name = 'ifj_job_definition_edge' AND c.name ="
                    + " 'route_key') OR (t.name = 'ifj_dag_gateway_decision' AND c.name ="
                    + " 'context_digest') OR (t.name = 'ifj_instance' AND c.name = 'wait_reason')"
                    + " OR (t.name = 'ifj_instance' AND c.name = 'permit_wait_deadline'))) = 15 AND"
                    + " EXISTS (SELECT 1 FROM sys.indexes WHERE object_id ="
                    + " OBJECT_ID('dbo.ifj_runtime_command') AND name ="
                    + " 'idx_ifj_runtime_command_expiration') AND EXISTS (SELECT 1 FROM"
                    + " sys.foreign_keys WHERE parent_object_id ="
                    + " OBJECT_ID('dbo.ifj_event_wait_operator') AND name ="
                    + " 'fk_ifj_wait_operator_wait') THEN 1 ELSE 0 END";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            ReleaseSchemaBoundary.verifyReady(connection);
            RuntimeGuardSchemaVerifier.verify(connection);
            TableIdSchemaVerifier.verify(connection);
            statement.setInt(1, REQUIRED_SCHEMA_VERSION);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw incompatibleSchema();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Database schema validation failed; iJob requires release V1 with logical"
                            + " schema 38",
                    exception);
        }
    }

    private IllegalStateException incompatibleSchema() {
        return new IllegalStateException(
                "Database release V1 with logical schema 38 is required; run the Flyway migrations"
                        + " before starting this application version");
    }
}
