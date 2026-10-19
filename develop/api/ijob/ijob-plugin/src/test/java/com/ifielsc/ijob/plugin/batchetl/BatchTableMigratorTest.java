package com.ifielsc.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.batchetl.metadata.BatchTablePlan;
import com.ifielsc.ijob.plugin.common.jdbc.JdbcIdentifierQuoter;
import com.ifielsc.ijob.plugin.common.jdbc.JdbcValueKey;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class BatchTableMigratorTest {
    @Test
    void shouldBuildBoundedLexicographicIncrementalQuery() throws Exception {
        BatchTablePlan.ColumnMapping id = mapping("ID", Types.BIGINT);
        BatchTablePlan.ColumnMapping tenant = mapping("TENANT_ID", Types.BIGINT);
        BatchTablePlan.ColumnMapping updated = mapping("UPDATED_AT", Types.TIMESTAMP);
        BatchTablePlan plan =
                new BatchTablePlan(
                        "APP",
                        "ORDERS",
                        "DW",
                        "ORDERS",
                        List.of(id, tenant, updated),
                        List.of(tenant, id),
                        updated);
        BatchEtlTaskConfig config = new BatchEtlTaskConfig();

        String sql = new BatchTableMigrator(null).selectSql(quoter(), plan, config, true);

        assertEquals(
                "SELECT `ID`,`TENANT_ID`,`UPDATED_AT` FROM "
                        + "`APP`.`ORDERS` WHERE (`UPDATED_AT` > ? OR "
                        + "(`UPDATED_AT` = ? AND (`TENANT_ID` > ? OR "
                        + "(`TENANT_ID` = ? AND `ID` > ?)))) AND "
                        + "`UPDATED_AT` <= ? ORDER BY `UPDATED_AT`,"
                        + "`TENANT_ID`,`ID`",
                sql);
    }

    @Test
    void shouldOrderKeyedFullExtractionForDuplicateDetection() throws Exception {
        BatchTablePlan.ColumnMapping id = mapping("ID", Types.BIGINT);
        BatchTablePlan plan =
                new BatchTablePlan(null, "ORDERS", null, "ORDERS", List.of(id), List.of(id), null);

        String sql =
                new BatchTableMigrator(null)
                        .selectSql(quoter(), plan, new BatchEtlTaskConfig(), false);

        assertEquals("SELECT `ID` FROM `ORDERS` ORDER BY `ID`", sql);
    }

    @Test
    void shouldDetectNonAdjacentAndBinaryEquivalentSourceKeys() {
        BatchTablePlan.ColumnMapping id = mapping("ID", Types.BINARY);
        BatchTablePlan plan =
                new BatchTablePlan(null, "ORDERS", null, "ORDERS", List.of(id), List.of(id), null);
        BatchTableMigrator migrator = new BatchTableMigrator(null);
        Set<JdbcValueKey> seen = new HashSet<>();

        migrator.ensureUniqueKey(new Object[] {new byte[] {1}}, seen, plan);
        migrator.ensureUniqueKey(new Object[] {new byte[] {2}}, seen, plan);

        assertThrows(
                IllegalArgumentException.class,
                () -> migrator.ensureUniqueKey(new Object[] {new byte[] {1}}, seen, plan));
    }

    private BatchTablePlan.ColumnMapping mapping(String name, int type) {
        return new BatchTablePlan.ColumnMapping(name, name, type, type);
    }

    private JdbcIdentifierQuoter quoter() throws Exception {
        DatabaseMetaData metadata =
                (DatabaseMetaData)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DatabaseMetaData.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getIdentifierQuoteString")
                                                ? "`"
                                                : null);
        return JdbcIdentifierQuoter.from(metadata);
    }
}
