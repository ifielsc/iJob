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

class SqlServerPlatformConventionTest {
    private static final String BASELINE = "db/migration/sqlserver/V19__platform_baseline.sql";
    private static final String CONDITIONAL_DAG = "db/migration/sqlserver/V21__conditional_dag.sql";
    private static final Pattern STATEMENT =
            Pattern.compile(
                    "(?s)<(?<tag>select|insert|update|delete)\\b(?<attrs>[^>]*)>(?<body>.*?)</\\k<tag>>");
    private static final Pattern ID = Pattern.compile("\\bid=\"(?<id>[^\"]+)\"");
    private static final Pattern FORBIDDEN_SQL =
            Pattern.compile(
                    "strftime|julianday|ON CONFLICT|ON DUPLICATE|INSERT IGNORE|"
                            + "CURRENT_TIMESTAMP\\(3\\)|LOCALTIMESTAMP\\(3\\)|ILIKE|"
                            + "IS NOT DISTINCT FROM|\\bINTERVAL\\b|\\bLIMIT\\s+|\\|\\|",
                    Pattern.CASE_INSENSITIVE);

    @Test
    void baselineShouldFreezeTheSqlServerV19Shape() throws IOException {
        String sql = read(new ClassPathResource(BASELINE));

        assertEquals(35, count(sql, "(?m)^CREATE TABLE dbo\\.ifj_"));
        assertEquals(35, count(sql, "PRIMARY KEY"));
        assertEquals(40, count(sql, "(?m)^ALTER TABLE dbo\\.ifj_.*FOREIGN KEY"));
        assertEquals(76, count(sql, "(?m)^CREATE INDEX "));
        assertEquals(11, count(sql, "(?m)^CREATE UNIQUE INDEX "));
        assertEquals(18, count(sql, "(?m)^CREATE TRIGGER dbo\\.trg_ifj_"));
        assertTrue(sql.contains("datetime2(3)"));
        assertTrue(sql.contains("SYSUTCDATETIME()"));
        assertTrue(sql.contains("Latin1_General_100_BIN2_UTF8"));
        assertTrue(sql.contains("Latin1_General_100_CI_AI_SC"));
        assertFalse(Pattern.compile("(?i)\\bIDENTITY\\b|\\bMERGE\\b").matcher(sql).find());
        assertFalse(Pattern.compile("(?i)timestamp\\(3\\)|\\bboolean\\b").matcher(sql).find());
    }

    @Test
    void conditionalDagShouldReleaseDependenciesAndSplitNewColumnBatches() throws IOException {
        String sql = read(new ClassPathResource(CONDITIONAL_DAG));

        int dropConstraint =
                sql.indexOf("DROP CONSTRAINT chk_ifj_job_definition_node_ref_snapshot");
        int dropIndex = sql.indexOf("DROP INDEX idx_ifj_job_definition_node_ref");
        int alterNodeType = sql.indexOf("ALTER COLUMN node_type varchar(32) NULL");
        int recreateIndex = sql.indexOf("CREATE INDEX idx_ifj_job_definition_node_ref");
        int recreateConstraint =
                sql.indexOf("ADD CONSTRAINT chk_ifj_job_definition_node_ref_snapshot");

        assertTrue(dropConstraint >= 0);
        assertTrue(dropIndex > dropConstraint);
        assertTrue(alterNodeType > dropIndex);
        assertTrue(recreateIndex > alterNodeType);
        assertTrue(recreateConstraint > recreateIndex);
        assertEquals(2, count(sql, "(?m)^GO$"));
    }

    @Test
    void sqlServerMapperOverridesShouldBePortableAndCoverSensitiveStatements() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml");
        Set<String> sqlServerStatements = new HashSet<>();
        boolean hasApiPagination = false;
        boolean hasBoundedTop = false;
        boolean hasQueueHints = false;

        for (Resource resource : resources) {
            String xml = read(resource);
            Matcher statement = STATEMENT.matcher(xml);
            while (statement.find()) {
                if (!statement.group("attrs").contains("databaseId=\"sqlserver\"")) {
                    continue;
                }
                Matcher id = ID.matcher(statement.group("attrs"));
                assertTrue(id.find(), resource::getDescription);
                String key =
                        resource.getFilename()
                                + ':'
                                + statement.group("tag")
                                + ':'
                                + id.group("id");
                assertTrue(
                        sqlServerStatements.add(key),
                        () -> "Duplicate SQL Server statement " + key);
                assertFalse(
                        FORBIDDEN_SQL.matcher(statement.group("body")).find(),
                        () -> "Foreign dialect SQL leaked into " + key);
                hasApiPagination |=
                        statement
                                .group("body")
                                .contains("OFFSET #{start} ROWS FETCH NEXT #{size} ROWS ONLY");
                hasBoundedTop |= statement.group("body").contains("TOP (#{limit})");
                hasQueueHints |=
                        statement.group("body").contains("WITH (UPDLOCK, READPAST, ROWLOCK)");
            }
        }

        assertEquals(134, sqlServerStatements.size());
        assertTrue(hasApiPagination);
        assertTrue(hasBoundedTop);
        assertTrue(hasQueueHints);
    }

    private String read(Resource resource) throws IOException {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String value, String regex) {
        return (int) Pattern.compile(regex).matcher(value).results().count();
    }
}
