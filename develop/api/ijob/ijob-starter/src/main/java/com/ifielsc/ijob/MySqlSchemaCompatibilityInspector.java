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
public class MySqlSchemaCompatibilityInspector implements SchemaCompatibilityInspector {
    static final int REQUIRED_SCHEMA_VERSION = 38;

    private final DataSource dataSource;

    public MySqlSchemaCompatibilityInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.MYSQL;
    }

    @Override
    public void verify() {
        String sql =
                "SELECT (SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = 1 AND"
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
                    + " success = 1) AND (SELECT COUNT(*) FROM information_schema.columns WHERE"
                    + " table_schema = DATABASE() AND table_name = 'ifj_app_run' AND column_name IN"
                    + " ('runtime_protocol_version', 'worker_task_ids', 'worker_job_ids')) = 3 AND"
                    + " (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema ="
                    + " DATABASE() AND table_name = 'ifj_instance' AND column_name IN"
                    + " ('wait_reason', 'permit_wait_deadline')) = 2";
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
