package com.ifielsc.ijob.infra.mapper.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.contract.enums.LogLevel;
import com.ifielsc.ijob.infra.entity.calendar.CalendarException;
import com.ifielsc.ijob.infra.entity.runtime.InstanceLog;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

class OracleRuntimeBindingTest {
    @Test
    void logColumnRenameMustPreserveEntityLevelBinding() throws Exception {
        var log = new InstanceLog();
        log.setLevel(LogLevel.INFO);
        var values = bind("runtime/InstanceLogMapper", log);
        assertEquals("INFO", values.get(4));
    }

    @Test
    void calendarColumnRenameMustPreserveEntityDateBinding() throws Exception {
        var exception = new CalendarException();
        exception.setDate(LocalDate.of(2026, 10, 7));
        var values = bind("calendar/CalendarExceptionMapper", exception);
        assertEquals(exception.getDate(), values.get(3));
    }

    private Map<Integer, Object> bind(String mapper, Object entity) throws Exception {
        var configuration = new Configuration();
        configuration.setDatabaseId("oracle");
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        String resource = "mapper/" + mapper + ".xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        var statement =
                configuration.getMappedStatement(
                        "com.ifielsc.ijob.infra.mapper." + mapper.replace('/', '.') + ".insert");
        var bound = statement.getBoundSql(entity);
        Map<Integer, Object> values = new HashMap<>();
        var prepared =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().startsWith("set")
                                            && !method.getName().equals("setNull")) {
                                        values.put((Integer) args[0], args[1]);
                                    }
                                    return null;
                                });
        assertDoesNotThrow(
                () ->
                        new DefaultParameterHandler(statement, entity, bound)
                                .setParameters(prepared));
        return values;
    }
}
