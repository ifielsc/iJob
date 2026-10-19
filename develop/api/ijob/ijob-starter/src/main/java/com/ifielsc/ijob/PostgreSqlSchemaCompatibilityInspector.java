package com.ifielsc.ijob;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.framework.database.schema.SchemaCompatibilityInspector;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

@Component
public class PostgreSqlSchemaCompatibilityInspector implements SchemaCompatibilityInspector {
    static final int REQUIRED_SCHEMA_VERSION = 38;

    private final DataSource dataSource;

    public PostgreSqlSchemaCompatibilityInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.POSTGRESQL;
    }

    @Override
    public void verify() {
        String sql =
                "SELECT (SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = TRUE AND"
                    + " ((capability_key = 'platform-schema' AND capability_version >= ?) OR"
                    + " (capability_key = 'event-wait-operator-relation' AND capability_version >="
                    + " 1) OR (capability_key = 'stable-id-segment-seed' AND capability_version >="
                    + " 1) OR (capability_key = 'execution-group-routing' AND capability_version >="
                    + " 1) OR (capability_key = 'conditional-dag' AND capability_version >= 1) OR"
                    + " (capability_key = 'execution-target-routing' AND capability_version >= 1)"
                    + " OR (capability_key = 'composite-task-concurrency' AND capability_version >="
                    + " 1) OR (capability_key = 'schedule-misfire-policy' AND capability_version >="
                    + " 1) OR (capability_key = 'runtime-history-v2' AND capability_version >= 1)))"
                    + " = 9 AND EXISTS (SELECT 1 FROM ifj_schema_history WHERE version = '1' AND"
                    + " success = TRUE) AND (SELECT COUNT(*) FROM information_schema.columns WHERE"
                    + " table_schema = current_schema() AND ((table_name = 'ifj_schema_capability'"
                    + " AND column_name = 'capability_key') OR (table_name ="
                    + " 'ifj_event_wait_operator' AND column_name = 'operator_id') OR (table_name ="
                    + " 'ifj_id_segment' AND column_name = 'next_value') OR (table_name ="
                    + " 'ifj_runtime_command' AND column_name = 'processing_fence') OR (table_name"
                    + " = 'ifj_trigger_work_item' AND column_name = 'fence_token') OR (table_name ="
                    + " 'ifj_app_run' AND column_name = 'runtime_protocol_version') OR (table_name"
                    + " = 'ifj_app_run' AND column_name = 'worker_task_ids') OR (table_name ="
                    + " 'ifj_app_run' AND column_name = 'worker_job_ids') OR (table_name ="
                    + " 'ifj_task_definition_group' AND column_name = 'group_code') OR (table_name"
                    + " = 'ifj_job_definition_group' AND column_name = 'group_code') OR (table_name"
                    + " = 'ifj_job_definition_node' AND column_name = 'node_kind') OR (table_name ="
                    + " 'ifj_job_definition_edge' AND column_name = 'route_key') OR (table_name ="
                    + " 'ifj_dag_gateway_decision' AND column_name = 'context_digest') OR"
                    + " (table_name = 'ifj_instance' AND column_name = 'wait_reason') OR"
                    + " (table_name = 'ifj_instance' AND column_name = 'permit_wait_deadline'))) ="
                    + " 15 AND EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = current_schema()"
                    + " AND indexname = 'idx_ifj_runtime_command_expiration') AND EXISTS (SELECT 1"
                    + " FROM information_schema.table_constraints WHERE table_schema ="
                    + " current_schema() AND constraint_name = 'fk_ifj_wait_operator_wait' AND"
                    + " constraint_type = 'FOREIGN KEY')";
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
