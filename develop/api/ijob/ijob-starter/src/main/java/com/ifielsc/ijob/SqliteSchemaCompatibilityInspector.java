// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

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
public class SqliteSchemaCompatibilityInspector implements SchemaCompatibilityInspector {
    static final int REQUIRED_SCHEMA_VERSION = 38;

    private static final String VERIFY_SQL =
            "SELECT (SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = 1 AND"
                + " ((capability_key = 'platform-schema' AND capability_version >= ?) OR"
                + " (capability_key = 'event-wait-operator-relation' AND capability_version >= 1)"
                + " OR (capability_key = 'stable-id-segment-seed' AND capability_version >= 1))) ="
                + " 3 AND EXISTS (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1 AND"
                + " capability_key = 'execution-group-routing' AND capability_version >= 1) AND"
                + " EXISTS (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1 AND"
                + " capability_key = 'conditional-dag' AND capability_version >= 1) AND EXISTS"
                + " (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                + " 'execution-target-routing' AND capability_version >= 1) AND EXISTS (SELECT 1"
                + " FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                + " 'composite-task-concurrency' AND capability_version >= 1) AND EXISTS (SELECT 1"
                + " FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                + " 'schedule-misfire-policy' AND capability_version >= 1) AND EXISTS (SELECT 1"
                + " FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                + " 'runtime-history-v2' AND capability_version >= 1) AND EXISTS (SELECT 1 FROM"
                + " ifj_schema_history WHERE version = '1' AND success = 1) AND (SELECT COUNT(*)"
                + " FROM (SELECT name FROM pragma_table_info('ifj_schema_capability') WHERE name ="
                + " 'capability_key' UNION ALL SELECT name FROM"
                + " pragma_table_info('ifj_event_wait_operator') WHERE name = 'operator_id' UNION"
                + " ALL SELECT name FROM pragma_table_info('ifj_id_segment') WHERE name ="
                + " 'next_value' UNION ALL SELECT name FROM"
                + " pragma_table_info('ifj_runtime_command') WHERE name = 'processing_fence' UNION"
                + " ALL SELECT name FROM pragma_table_info('ifj_trigger_work_item') WHERE name ="
                + " 'fence_token' UNION ALL SELECT name FROM pragma_table_info('ifj_app_run') WHERE"
                + " name = 'runtime_protocol_version' UNION ALL SELECT name FROM"
                + " pragma_table_info('ifj_app_run') WHERE name = 'worker_task_ids' UNION ALL"
                + " SELECT name FROM pragma_table_info('ifj_app_run') WHERE name = 'worker_job_ids'"
                + " UNION ALL SELECT name FROM pragma_table_info('ifj_task_definition_group') WHERE"
                + " name = 'group_code' UNION ALL SELECT name FROM"
                + " pragma_table_info('ifj_job_definition_group') WHERE name = 'group_code' UNION"
                + " ALL SELECT name FROM pragma_table_info('ifj_job_definition_node') WHERE name ="
                + " 'node_kind' UNION ALL SELECT name FROM"
                + " pragma_table_info('ifj_job_definition_edge') WHERE name = 'route_key' UNION ALL"
                + " SELECT name FROM pragma_table_info('ifj_dag_gateway_decision') WHERE name ="
                + " 'context_digest' UNION ALL SELECT name FROM pragma_table_info('ifj_instance')"
                + " WHERE name = 'wait_reason' UNION ALL SELECT name FROM"
                + " pragma_table_info('ifj_instance') WHERE name = 'permit_wait_deadline')) = 15"
                + " AND EXISTS (SELECT 1 FROM sqlite_master WHERE type = 'index' AND name ="
                + " 'idx_ifj_runtime_command_expiration') AND EXISTS (SELECT 1 FROM"
                + " pragma_foreign_key_list('ifj_event_wait_operator') WHERE \"table\" ="
                + " 'ifj_event_wait' AND \"from\" = 'wait_id' AND \"to\" = 'id') AND NOT EXISTS"
                + " (SELECT 1 FROM pragma_foreign_key_check)";

    private final DataSource dataSource;

    public SqliteSchemaCompatibilityInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.SQLITE;
    }

    @Override
    public void verify() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(VERIFY_SQL)) {
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
