package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SqlitePlatformConventionTest {
    private static final String BASELINE = "db/migration/sqlite/V19__platform_baseline.sql";
    private static final Pattern STATEMENT =
            Pattern.compile(
                    "(?s)<(?<tag>select|insert|update|delete)\\b(?<attrs>[^>]*)>(?<body>.*?)</\\k<tag>>");
    private static final Pattern ID = Pattern.compile("\\bid=\"(?<id>[^\"]+)\"");
    private static final Pattern MYSQL_OR_POSTGRESQL_ONLY =
            Pattern.compile(
                    "\\bINTERVAL\\b|\\bEXTRACT\\b|CURRENT_TIMESTAMP\\(3\\)|LOCALTIMESTAMP\\(3\\)|ILIKE|FOR"
                        + " UPDATE|TIMESTAMPADD|TIMESTAMPDIFF|DATE_ADD|ON DUPLICATE|INSERT"
                        + " IGNORE|CONCAT\\(|GREATEST\\(|IS NOT DISTINCT FROM|"
                        + "POSITION\\(|FIND_IN_SET|\\bUSING\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_DIALECT =
            Pattern.compile(
                    "CURRENT_TIMESTAMP\\(3\\)|LOCALTIMESTAMP\\(3\\)|TIMESTAMPADD|TIMESTAMPDIFF|DATE_ADD|ON"
                        + " DUPLICATE|INSERT IGNORE|FOR UPDATE|FIND_IN_SET|CONCAT\\(",
                    Pattern.CASE_INSENSITIVE);

    @Test
    void baselineShouldFreezeTheV19StandaloneShape() throws IOException {
        String sql = read(new ClassPathResource(BASELINE));

        assertEquals(35, count(sql, "(?m)^CREATE TABLE "));
        assertEquals(35, count(sql, "WITHOUT ROWID;"));
        assertEquals(40, count(sql, "FOREIGN KEY"));
        assertEquals(76, count(sql, "(?m)^CREATE INDEX "));
        assertEquals(18, count(sql, "(?m)^CREATE TRIGGER "));
        assertEquals(91, count(sql, "_timestamp CHECK"));
        assertEquals(1, count(sql, "_date CHECK"));
        assertTrue(sql.contains("strftime('%Y-%m-%d %H:%M:%f','now')"));
        assertTrue(sql.contains("chk_sqlite_ifj_event_binding_enabled_bool"));
        assertTrue(sql.contains("chk_sqlite_ifj_task_id_id"));
        assertFalse(
                Pattern.compile("(?i)\\b(?:ALTER TABLE|AUTOINCREMENT|SERIAL|IDENTITY|PLPGSQL)\\b")
                        .matcher(sql)
                        .find());
        assertFalse(Pattern.compile("(?i)\\b(?:bigint|boolean|varchar)\\b").matcher(sql).find());
    }

    @Test
    void everyDialectSensitiveMapperShouldHaveAParsableSqliteVariant() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml");
        Set<String> sqliteStatements = new HashSet<>();
        Set<String> requiredStatements = new HashSet<>();

        for (Resource resource : resources) {
            String xml = read(resource);
            Matcher statement = STATEMENT.matcher(xml);
            while (statement.find()) {
                Matcher id = ID.matcher(statement.group("attrs"));
                if (!id.find()) {
                    continue;
                }
                String key =
                        resource.getFilename()
                                + ':'
                                + statement.group("tag")
                                + ':'
                                + id.group("id");
                String attributes = statement.group("attrs");
                if (attributes.contains("databaseId=\"sqlite\"")) {
                    sqliteStatements.add(key);
                    assertFalse(
                            MYSQL_OR_POSTGRESQL_ONLY.matcher(statement.group()).find(),
                            () -> "SQLite-only SQL leaked into " + key);
                } else if (!attributes.contains("databaseId=")
                        && GENERIC_DIALECT.matcher(statement.group("body")).find()) {
                    requiredStatements.add(key);
                }
            }
        }

        assertEquals(117, sqliteStatements.size());
        assertTrue(
                sqliteStatements.containsAll(requiredStatements),
                () ->
                        "Missing SQLite variants: "
                                + difference(requiredStatements, sqliteStatements));
    }

    private String read(Resource resource) throws IOException {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String value, String regex) {
        return (int) Pattern.compile(regex).matcher(value).results().count();
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
