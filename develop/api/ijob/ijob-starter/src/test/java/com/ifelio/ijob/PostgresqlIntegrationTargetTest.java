package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostgresqlIntegrationTargetTest {
    @Test
    void onlyAcceptsAnExplicitIsolatedDatabasePath() {
        assertEquals(
                "ijob_it_pg_release",
                RealPostgresqlMigrationIT.integrationDatabaseName(
                        "jdbc:postgresql://localhost:5432/ijob_it_pg_release?sslmode=disable"));
        for (String url :
                new String[] {
                    "jdbc:postgresql://localhost/production?path=/ijob_it_pg_release",
                    "jdbc:postgresql://localhost/production/ijob_it_pg_release",
                    "jdbc:postgresql://localhost/%69job_it_pg_release",
                    "jdbc:postgresql://localhost/ijob_it_pg_release#prod",
                    "jdbc:postgresql://user@localhost/ijob_it_pg_release",
                    "jdbc:postgresql://localhost/ijob_it_pg_",
                    "jdbc:postgresql://localhost/production"
                }) {
            assertThrows(
                    IllegalStateException.class,
                    () -> RealPostgresqlMigrationIT.integrationDatabaseName(url));
        }
    }
}
