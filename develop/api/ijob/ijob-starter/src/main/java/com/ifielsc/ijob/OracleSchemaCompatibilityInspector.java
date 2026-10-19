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
public class OracleSchemaCompatibilityInspector implements SchemaCompatibilityInspector {
    static final int REQUIRED_SCHEMA_VERSION = 38;

    private final DataSource dataSource;

    public OracleSchemaCompatibilityInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.ORACLE;
    }

    @Override
    public void verify() {
        String sql =
                "SELECT CASE WHEN (SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = 1 AND"
                    + " ((capability_key = 'platform-schema' AND capability_version >= ?) OR"
                    + " (capability_key = 'event-wait-operator-relation' AND capability_version >="
                    + " 1) OR (capability_key = 'stable-id-segment-seed' AND capability_version >="
                    + " 1))) = 3 AND EXISTS (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1"
                    + " AND capability_key = 'execution-group-routing' AND capability_version >= 1)"
                    + " AND EXISTS (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1 AND"
                    + " capability_key = 'conditional-dag' AND capability_version >= 1) AND EXISTS"
                    + " (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                    + " 'execution-target-routing' AND capability_version >= 1) AND EXISTS (SELECT"
                    + " 1 FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                    + " 'composite-task-concurrency' AND capability_version >= 1) AND EXISTS"
                    + " (SELECT 1 FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                    + " 'schedule-misfire-policy' AND capability_version >= 1) AND EXISTS (SELECT 1"
                    + " FROM ifj_schema_capability WHERE enabled = 1 AND capability_key ="
                    + " 'runtime-history-v2' AND capability_version >= 1) AND EXISTS (SELECT 1 FROM"
                    + " \"ifj_schema_history\" WHERE \"version\" = '1' AND \"success\" = 1) AND"
                    + " (SELECT COUNT(*) FROM user_tab_columns WHERE (table_name ="
                    + " 'IFJ_SCHEMA_CAPABILITY' AND column_name = 'CAPABILITY_KEY') OR (table_name"
                    + " = 'IFJ_EVENT_WAIT_OPERATOR' AND column_name = 'OPERATOR_ID') OR (table_name"
                    + " = 'IFJ_ID_SEGMENT' AND column_name = 'NEXT_VALUE') OR (table_name ="
                    + " 'IFJ_RUNTIME_COMMAND' AND column_name = 'PROCESSING_FENCE') OR (table_name"
                    + " = 'IFJ_TRIGGER_WORK_ITEM' AND column_name = 'FENCE_TOKEN') OR (table_name ="
                    + " 'IFJ_APP_RUN' AND column_name = 'RUNTIME_PROTOCOL_VERSION') OR (table_name"
                    + " = 'IFJ_APP_RUN' AND column_name = 'WORKER_TASK_IDS') OR (table_name ="
                    + " 'IFJ_APP_RUN' AND column_name = 'WORKER_JOB_IDS') OR (table_name ="
                    + " 'IFJ_TASK_DEFINITION_GROUP' AND column_name = 'GROUP_CODE') OR (table_name"
                    + " = 'IFJ_JOB_DEFINITION_GROUP' AND column_name = 'GROUP_CODE') OR (table_name"
                    + " = 'IFJ_JOB_DEFINITION_NODE' AND column_name = 'NODE_KIND') OR (table_name ="
                    + " 'IFJ_JOB_DEFINITION_EDGE' AND column_name = 'ROUTE_KEY') OR (table_name ="
                    + " 'IFJ_DAG_GATEWAY_DECISION' AND column_name = 'CONTEXT_DIGEST') OR"
                    + " (table_name = 'IFJ_INSTANCE' AND column_name = 'WAIT_REASON') OR"
                    + " (table_name = 'IFJ_INSTANCE' AND column_name = 'PERMIT_WAIT_DEADLINE')) ="
                    + " 15 AND EXISTS (SELECT 1 FROM user_ind_columns WHERE table_name ="
                    + " 'IFJ_RUNTIME_COMMAND' AND column_name = 'EXPIRES_AT') AND EXISTS (SELECT 1"
                    + " FROM user_constraints WHERE table_name = 'IFJ_EVENT_WAIT_OPERATOR' AND"
                    + " constraint_type = 'R') THEN 1 ELSE 0 END FROM dual";
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
