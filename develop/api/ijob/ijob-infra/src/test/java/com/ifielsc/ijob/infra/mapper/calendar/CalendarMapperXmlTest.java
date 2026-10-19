// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.calendar.CalendarPageQuery;
import com.ifielsc.ijob.contract.enums.CalendarDateType;
import com.ifielsc.ijob.infra.entity.calendar.CalendarException;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

class CalendarMapperXmlTest {
    private static final String CALENDAR_MAPPER = "mapper/calendar/CalendarMapper.xml";
    private static final String EXCEPTION_MAPPER = "mapper/calendar/CalendarExceptionMapper.xml";

    @Test
    void mapperXmlShouldParseAndExposeAggregateStatements() throws IOException {
        Configuration configuration = parseMappers();
        List.of(
                        calendar("selectList"),
                        calendar("selectById"),
                        calendar("selectByName"),
                        calendar("insert"),
                        calendar("updateById"),
                        calendar("deleteById"),
                        calendar("selectIdsForUpdate"),
                        calendar("deleteByIds"),
                        exception("selectByCalendarId"),
                        exception("insert"),
                        exception("deleteByCalendarId"))
                .forEach(
                        id ->
                                assertTrue(
                                        configuration.hasStatement(id, false),
                                        () -> "Missing mapped statement: " + id));
    }

    @Test
    void batchDeleteShouldLockInOrderAndBeEmptySafe() throws IOException {
        Configuration configuration = parseMappers();
        String lock =
                normalizedSql(
                        configuration
                                .getMappedStatement(calendar("selectIdsForUpdate"))
                                .getBoundSql(Map.of("ids", List.of(2L, 1L)))
                                .getSql());
        String emptyDelete =
                normalizedSql(
                        configuration
                                .getMappedStatement(calendar("deleteByIds"))
                                .getBoundSql(Map.of("ids", List.of()))
                                .getSql());

        assertTrue(lock.contains("id IN ( ? , ? )"));
        assertTrue(lock.endsWith("ORDER BY id FOR UPDATE"));
        assertTrue(emptyDelete.contains("WHERE 1 = 0"));
    }

    @Test
    void listSqlShouldBindNameAndUseFixedOrdering() throws IOException {
        Configuration configuration = parseMappers();
        CalendarPageQuery query = new CalendarPageQuery();
        query.setName("business");

        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(calendar("selectList"))
                                .getBoundSql(query)
                                .getSql());

        assertTrue(sql.contains("FROM ifj_calendar"));
        assertTrue(sql.contains("name LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.endsWith("ORDER BY update_time DESC, id DESC"));
        assertFalse(readResource(CALENDAR_MAPPER).contains("${"));
    }

    @Test
    void exceptionSqlShouldBindAggregateValuesWithoutTextReplacement() throws IOException {
        Configuration configuration = parseMappers();
        CalendarException item = new CalendarException();
        item.setId(1L);
        item.setCalendarId(7L);
        item.setDate(LocalDate.of(2026, 8, 10));
        item.setType(CalendarDateType.HOLIDAY);

        String insertSql =
                normalizedSql(
                        configuration
                                .getMappedStatement(exception("insert"))
                                .getBoundSql(item)
                                .getSql());
        String deleteSql =
                normalizedSql(
                        configuration
                                .getMappedStatement(exception("deleteByCalendarId"))
                                .getBoundSql(Map.of("calendarId", 7L))
                                .getSql());

        assertTrue(insertSql.startsWith("INSERT INTO ifj_calendar_exception"));
        assertTrue(insertSql.contains("VALUES ( ?, ?, ?, ?, ?, ? )"));
        assertTrue(deleteSql.contains("calendar_id = ?"));
        assertFalse(readResource(EXCEPTION_MAPPER).contains("${"));
    }

    private Configuration parseMappers() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parseMapper(configuration, CALENDAR_MAPPER);
        parseMapper(configuration, EXCEPTION_MAPPER);
        return configuration;
    }

    private void parseMapper(Configuration configuration, String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, path, configuration.getSqlFragments());
            builder.parse();
        }
    }

    private String readResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String calendar(String id) {
        return "com.ifielsc.ijob.infra.mapper.calendar.CalendarMapper." + id;
    }

    private String exception(String id) {
        return "com.ifielsc.ijob.infra.mapper.calendar." + "CalendarExceptionMapper." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
