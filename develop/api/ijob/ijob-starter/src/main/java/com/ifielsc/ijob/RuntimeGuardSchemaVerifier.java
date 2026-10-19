// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import java.sql.Connection;
import java.sql.SQLException;

final class RuntimeGuardSchemaVerifier {
    private RuntimeGuardSchemaVerifier() {}

    static void verify(Connection connection) throws SQLException {
        try (var statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM ifj_runtime_guard WHERE guard_key IN"
                                        + " ('concurrency-policy', 'runtime-lifecycle',"
                                        + " 'auth-accounts') AND revision >= 0");
                var result = statement.executeQuery()) {
            if (!result.next() || result.getInt(1) != 3) {
                throw new IllegalStateException("Schema V27 runtime guard seeds are missing");
            }
        }
        try (var statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM ifj_schedule WHERE revision IS NULL");
                var result = statement.executeQuery()) {
            if (!result.next() || result.getLong(1) != 0) {
                throw new IllegalStateException("Schema V27 schedule revision is invalid");
            }
        }
        // Empty projections validate required columns even before the first account is initialized.
        try (var statement =
                        connection.prepareStatement(
                                "SELECT id, login_id, username, display_name, password_hash,"
                                    + " role_code, enabled, must_change_password, security_version,"
                                    + " revision, created_by, create_time, update_time FROM"
                                    + " ifj_auth_user WHERE 1=0");
                var result = statement.executeQuery()) {
            if (result.getMetaData().getColumnCount() != 13) {
                throw new IllegalStateException("Schema V27 account columns are missing");
            }
        }
        try (var statement =
                        connection.prepareStatement(
                                "SELECT id, occurred_at, actor_login_id, actor_name, action_code,"
                                    + " target_type, target_id, result_code, trace_id, request_id,"
                                    + " changed_fields FROM ifj_operation_audit WHERE 1=0");
                var result = statement.executeQuery()) {
            if (result.getMetaData().getColumnCount() != 11) {
                throw new IllegalStateException("Schema V27 audit columns are missing");
            }
        }
    }
}
