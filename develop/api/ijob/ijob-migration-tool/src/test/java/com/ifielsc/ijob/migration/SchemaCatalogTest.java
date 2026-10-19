package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

class SchemaCatalogTest {

    @Test
    void freezesTheV27PortableCatalog() {
        assertEquals(27, SchemaCatalog.LOGICAL_SCHEMA_VERSION);
        assertEquals(45, SchemaCatalog.TABLES.size());
        assertEquals(45, new HashSet<>(SchemaCatalog.TABLES).size());
        assertEquals("ifj_app_run", SchemaCatalog.TABLES.getFirst());
        assertEquals("ifj_trigger_work_item", SchemaCatalog.TABLES.getLast());
        assertTrue(SchemaCatalog.TABLES.contains("ifj_dag_gateway_decision"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_plugin_concurrency_policy"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_concurrency_resource"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_task_concurrency_policy"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_task_concurrency_resource"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_schedule_misfire_audit"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_auth_user"));
        assertTrue(SchemaCatalog.TABLES.contains("ifj_operation_audit"));
        assertFalse(SchemaCatalog.TABLES.contains("ifj_concurrency_policy"));
    }

    @Test
    void freezesCrossProductLogicalTypeOverrides() {
        assertTrue(SchemaCatalog.isBoolean("ifj_task", "enabled"));
        assertTrue(SchemaCatalog.isBoolean("ifj_auth_user", "enabled"));
        assertTrue(SchemaCatalog.isBoolean("ifj_auth_user", "must_change_password"));
        assertTrue(SchemaCatalog.isBoolean("ifj_task", "retired"));
        assertFalse(SchemaCatalog.isBoolean("ifj_task", "current_revision"));
        assertTrue(SchemaCatalog.isTimestamp("create_time"));
        assertTrue(SchemaCatalog.isTimestamp("fire_claimed_at"));
        assertTrue(SchemaCatalog.isDate("ifj_calendar_exception", "date"));
        assertEquals(
                "calendar_date",
                SchemaCatalog.physicalColumn("Oracle", "ifj_calendar_exception", "date"));
        assertEquals(
                "level", SchemaCatalog.logicalColumn("Oracle", "ifj_instance_log", "log_level"));
    }
}
