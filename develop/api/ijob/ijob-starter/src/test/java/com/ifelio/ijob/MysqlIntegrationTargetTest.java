package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MysqlIntegrationTargetTest {
    @Test
    void onlyAcceptsAnExplicitIsolatedDatabasePath() {
        assertEquals(
                "ijob_it_release",
                RealMysqlMigrationIT.integrationDatabaseName(
                        "jdbc:mysql://localhost:3306/ijob_it_release?useSSL=false"));
        for (String url :
                new String[] {
                    "jdbc:mysql://localhost/prod?path=/ijob_it_release",
                    "jdbc:mysql://localhost/prod/ijob_it_release",
                    "jdbc:mysql://localhost/%69job_it_release",
                    "jdbc:mysql://localhost/ijob_it_release#prod",
                    "jdbc:mysql://user@localhost/ijob_it_release",
                    "jdbc:mysql://localhost/ijob_it_",
                    "jdbc:mysql://localhost/production"
                }) {
            assertThrows(
                    IllegalStateException.class,
                    () -> RealMysqlMigrationIT.integrationDatabaseName(url));
        }
    }
}
