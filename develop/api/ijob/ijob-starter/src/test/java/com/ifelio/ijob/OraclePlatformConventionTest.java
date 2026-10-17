package com.ifelio.ijob;

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

class OraclePlatformConventionTest {
    private static final String BASELINE = "db/migration/oracle/V19__platform_baseline.sql";
    private static final Pattern STATEMENT =
            Pattern.compile(
                    "(?s)<(?<tag>select|insert|update|delete)\\b(?<attrs>[^>]*)>(?<body>.*?)</\\k<tag>>");
    private static final Pattern ID = Pattern.compile("\\bid=\"(?<id>[^\"]+)\"");
    private static final Pattern FOREIGN_DIALECT =
            Pattern.compile(
                    "strftime|julianday|ON CONFLICT|ON DUPLICATE|INSERT IGNORE|"
                            + "CURRENT_TIMESTAMP\\(3\\)|LOCALTIMESTAMP\\(3\\)|ILIKE|"
                            + "IS NOT DISTINCT FROM|\\bLIMIT\\s+|OFFSET\\s+|FETCH\\s+NEXT|"
                            + "\\bTRUE\\b|\\bFALSE\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECT_NAME =
            Pattern.compile(
                    "(?im)(?:CONSTRAINT|CREATE(?: UNIQUE)? INDEX|CREATE OR REPLACE"
                            + " TRIGGER)\\s+(?<name>[a-z][a-z0-9_]*)");

    @Test
    void baselineShouldFreezeTheOracleElevenGR2V19Shape() throws IOException {
        String sql = read(new ClassPathResource(BASELINE));

        assertTrue(sql.startsWith("-- Oracle 11g R2+ platform baseline"));
        assertEquals(35, count(sql, "(?m)^CREATE TABLE ifj_"));
        assertEquals(35, count(sql, "PRIMARY KEY"));
        assertEquals(40, count(sql, "(?m)^ALTER TABLE ifj_.*FOREIGN KEY"));
        assertEquals(76, count(sql, "(?m)^CREATE INDEX "));
        assertEquals(18, count(sql, "(?m)^CREATE OR REPLACE TRIGGER "));
        assertTrue(sql.contains("TIMESTAMP(3)"));
        assertTrue(sql.contains("CLOB"));
        assertTrue(sql.contains("calendar_date DATE"));
        assertTrue(sql.contains("log_level VARCHAR2"));
        assertTrue(sql.contains("state_value CLOB,"));
        assertTrue(sql.contains("VALUES ('platform-schema', 19, 1"));
        assertFalse(
                Pattern.compile(
                                "(?im)^\\s*CREATE SEQUENCE|GENERATED\\s+.*\\bIDENTITY\\b|"
                                        + "AUTO_INCREMENT|\\bSERIAL\\b")
                        .matcher(sql)
                        .find());
        Matcher names = OBJECT_NAME.matcher(sql);
        while (names.find()) {
            String name = names.group("name");
            assertTrue(
                    name.getBytes(StandardCharsets.UTF_8).length <= 30,
                    () -> "Oracle 11g object name exceeds 30 bytes: " + name);
        }
    }

    @Test
    void oracleMapperOverridesShouldCoverSqliteSensitiveStatementsAndStayOnElevenGSyntax()
            throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml");
        Set<String> oracleStatements = new HashSet<>();
        Set<String> sqliteStatements = new HashSet<>();
        boolean hasElevenGPagination = false;
        boolean hasSkipLockedQueue = false;
        boolean hasAtomicClobUpsert = false;

        for (Resource resource : resources) {
            Matcher statement = STATEMENT.matcher(read(resource));
            while (statement.find()) {
                Matcher id = ID.matcher(statement.group("attrs"));
                assertTrue(id.find(), resource::getDescription);
                String key =
                        resource.getFilename()
                                + ':'
                                + statement.group("tag")
                                + ':'
                                + id.group("id");
                if (statement.group("attrs").contains("databaseId=\"sqlite\"")) {
                    sqliteStatements.add(key);
                }
                if (!statement.group("attrs").contains("databaseId=\"oracle\"")) {
                    continue;
                }
                String body = statement.group("body");
                assertTrue(oracleStatements.add(key), () -> "Duplicate Oracle statement " + key);
                assertFalse(
                        FOREIGN_DIALECT.matcher(body).find(),
                        () -> "Foreign or post-11g SQL leaked into " + key);
                assertFalse(body.contains("${"), () -> "Text substitution leaked into " + key);
                hasElevenGPagination |=
                        body.contains("ROWNUM AS ijob_row_no_")
                                && body.contains("WHERE ijob_row_no_ &gt; #{start}");
                hasSkipLockedQueue |=
                        body.contains("ROWNUM &lt;= #{limit}")
                                && body.contains("FOR UPDATE SKIP LOCKED");
                hasAtomicClobUpsert |=
                        key.endsWith("ClusterStateMapper.xml:insert:upsert")
                                && body.contains("new_state_value CLOB")
                                && body.contains("WHEN DUP_VAL_ON_INDEX");
            }
        }

        assertEquals(152, oracleStatements.size());
        assertTrue(
                oracleStatements.containsAll(sqliteStatements),
                () -> "Missing Oracle variants: " + difference(sqliteStatements, oracleStatements));
        assertTrue(hasElevenGPagination);
        assertTrue(hasSkipLockedQueue);
        assertTrue(hasAtomicClobUpsert);
    }

    @Test
    void nullableNodeUniquenessCorrectionMustKeepAllVendorVersionsAligned() throws IOException {
        String migration = "V33__nullable_instance_node_uniqueness.sql";
        for (String vendor : Set.of("mysql", "postgresql", "sqlserver", "sqlite")) {
            String sql = read(new ClassPathResource("db/migration/" + vendor + "/" + migration));
            assertTrue(sql.contains("SELECT 1;"));
            assertFalse(sql.contains("ALTER TABLE"));
        }
        String sql = read(new ClassPathResource("db/migration/oracle/" + migration));
        assertEquals(2, count(sql, "CREATE UNIQUE INDEX"));
        assertEquals(
                2, count(sql, "CASE WHEN parent_instance_id IS NOT NULL AND node_id IS NOT NULL"));
        assertEquals(
                2,
                count(
                        sql,
                        "CASE WHEN parent_instance_id IS NOT NULL AND definition_node_id IS NOT"
                                + " NULL"));
        assertTrue(sql.indexOf("CREATE UNIQUE INDEX") < sql.indexOf("DROP CONSTRAINT"));
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
