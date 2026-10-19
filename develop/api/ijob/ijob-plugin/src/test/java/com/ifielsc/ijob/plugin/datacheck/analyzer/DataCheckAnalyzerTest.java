package com.ifielsc.ijob.plugin.datacheck.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.plugin.datacheck.config.CheckLevel;
import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.CompareType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class DataCheckAnalyzerTest {
    @Test
    void normalShouldCompareNumbersWithoutScaleAndStringsByOrder() {
        NormalCheckAnalyzer analyzer = new NormalCheckAnalyzer();
        DataCheckItemConfig numeric =
                item(CheckType.NORMAL, CompareType.EQ, new BigDecimal("1.00"));
        DataCheckItemConfig text = item(CheckType.NORMAL, CompareType.GT, "beta");

        assertTrue(analyzer.analyze(numeric, 1).isSuccess());
        assertTrue(analyzer.analyze(text, "gamma").isSuccess());
        assertFalse(analyzer.analyze(text, "alpha").isSuccess());
    }

    @Test
    void normalShouldCompareNumericJdbcTextAgainstNumericExpectation() {
        NormalCheckAnalyzer analyzer = new NormalCheckAnalyzer();
        DataCheckItemConfig greater = item(CheckType.NORMAL, CompareType.GT, 2);
        DataCheckItemConfig between = item(CheckType.NORMAL, CompareType.BETWEEN, null);
        between.setMinValue(2);
        between.setMaxValue(20);

        assertTrue(analyzer.analyze(greater, "10").isSuccess());
        assertTrue(analyzer.analyze(between, "10").isSuccess());
    }

    @Test
    void countShouldSupportAllComparatorsAndInclusiveBetween() {
        CountCheckAnalyzer analyzer = new CountCheckAnalyzer();
        for (CompareType type : List.of(CompareType.EQ, CompareType.GE, CompareType.LE)) {
            DataCheckItemConfig config = item(CheckType.COUNT, type, 5);
            assertTrue(analyzer.analyze(config, 5L).isSuccess());
        }
        DataCheckItemConfig between = item(CheckType.COUNT, CompareType.BETWEEN, null);
        between.setMinValue(3);
        between.setMaxValue(5);
        assertTrue(analyzer.analyze(between, 3L).isSuccess());
        assertTrue(analyzer.analyze(between, 5L).isSuccess());
        assertFalse(analyzer.analyze(between, 6L).isSuccess());
    }

    @Test
    void existsDefaultsToTrueAndSupportsNe() {
        ExistsCheckAnalyzer analyzer = new ExistsCheckAnalyzer();
        DataCheckItemConfig defaultTrue = item(CheckType.EXISTS, null, null);
        DataCheckItemConfig notExpected = item(CheckType.EXISTS, CompareType.NE, false);

        assertTrue(analyzer.analyze(defaultTrue, true).isSuccess());
        assertFalse(analyzer.analyze(defaultTrue, false).isSuccess());
        assertTrue(analyzer.analyze(notExpected, true).isSuccess());
    }

    @Test
    void emptyShouldAcceptBooleanAndLegacyCollectionResults() {
        EmptyCheckAnalyzer analyzer = new EmptyCheckAnalyzer();
        DataCheckItemConfig config = item(CheckType.EMPTY, null, null);

        assertTrue(analyzer.analyze(config, null).isSuccess());
        assertTrue(analyzer.analyze(config, true).isSuccess());
        assertFalse(analyzer.analyze(config, false).isSuccess());
        assertTrue(analyzer.analyze(config, List.of()).isSuccess());
        assertFalse(analyzer.analyze(config, List.of("row")).isSuccess());
        assertFalse(analyzer.analyze(config, "").isSuccess());
    }

    @Test
    void resultShouldRetainNameExpectedMessageAndLevel() {
        CountCheckAnalyzer analyzer = new CountCheckAnalyzer();
        DataCheckItemConfig config = item(CheckType.COUNT, CompareType.EQ, 2);
        config.setName("row-count");
        config.setLevel(CheckLevel.WARN);

        DataCheckItemResult result = analyzer.analyze(config, 1L);

        assertFalse(result.isSuccess());
        assertEquals("row-count", result.getName());
        assertEquals(2, result.getExpected());
        assertEquals("count check failed", result.getMessage());
        assertEquals(CheckLevel.WARN, result.getLevel());
    }

    @Test
    void compositeShouldRouteAndRejectMissingAnalyzer() {
        CompositeDataCheckAnalyzer composite =
                new CompositeDataCheckAnalyzer(
                        List.of(
                                new NormalCheckAnalyzer(),
                                new CountCheckAnalyzer(),
                                new ExistsCheckAnalyzer(),
                                new EmptyCheckAnalyzer()));
        DataCheckItemConfig normal = item(CheckType.NORMAL, CompareType.EQ, "OK");
        assertTrue(composite.analyze(normal, "OK").isSuccess());

        normal.setType(null);
        assertThrows(IllegalArgumentException.class, () -> composite.analyze(normal, "OK"));
    }

    private DataCheckItemConfig item(CheckType type, CompareType compare, Object expected) {
        DataCheckItemConfig item = new DataCheckItemConfig();
        item.setName("check");
        item.setType(type);
        item.setCompareType(compare);
        item.setExpected(expected);
        item.setLevel(CheckLevel.ERROR);
        return item;
    }
}
