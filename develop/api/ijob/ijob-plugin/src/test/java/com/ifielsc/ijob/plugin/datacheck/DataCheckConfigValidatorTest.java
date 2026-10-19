// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.plugin.datacheck.config.CheckLevel;
import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.CompareType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.config.HandlerConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DataCheckConfigValidatorTest {
    private final DataCheckConfigValidator validator = new DataCheckConfigValidator();

    @Test
    void fourCheckTypesAndHandlersShouldBeAccepted() {
        DataCheckConfig config =
                base(
                        item("normal", CheckType.NORMAL, CompareType.EQ, "OK"),
                        item("count", CheckType.COUNT, CompareType.BETWEEN, null),
                        item("exists", CheckType.EXISTS, CompareType.NE, false),
                        item("empty", CheckType.EMPTY, null, null));
        config.getChecks().get(1).setMinValue(1);
        config.getChecks().get(1).setMaxValue(5);
        config.setHandlers(
                List.of(
                        handler("VARIABLE", Map.of()),
                        handler("NOTIFY", Map.of("notifyConfigId", 7, "notifyOnSuccess", false))));

        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void resourceChecksAndReadQueriesAreRequired() {
        DataCheckConfig config = base(item("normal", CheckType.NORMAL, CompareType.EQ, "OK"));
        config.setResourceId(0L);
        assertInvalid(config);

        config = base(item("normal", CheckType.NORMAL, CompareType.EQ, "OK"));
        config.getChecks().getFirst().setSql("update orders set state='OK'");
        assertInvalid(config);

        config = base();
        assertInvalid(config);
    }

    @Test
    void commentsParenthesesAndSafeCteShouldBeAccepted() {
        DataCheckConfig commented = base(item("commented", CheckType.NORMAL, CompareType.EQ, "OK"));
        commented.getChecks().getFirst().setSql("/* report */ -- next line\n((select 'update'));");
        DataCheckConfig cte = base(item("cte", CheckType.NORMAL, CompareType.EQ, "OK"));
        cte.getChecks().getFirst().setSql("with source as (select 1) select * from source");

        assertDoesNotThrow(() -> validator.validate(commented));
        assertDoesNotThrow(() -> validator.validate(cte));
    }

    @Test
    void mutatingCteAndLockingSelectShouldBeRejected() {
        DataCheckConfig mutatingCte = base(item("cte", CheckType.NORMAL, CompareType.EQ, "OK"));
        mutatingCte
                .getChecks()
                .getFirst()
                .setSql(
                        "with changed as (delete from orders returning *) "
                                + "select * from changed");
        DataCheckConfig selectInto = base(item("into", CheckType.NORMAL, CompareType.EQ, "OK"));
        selectInto.getChecks().getFirst().setSql("select * into archived_orders from orders");
        DataCheckConfig locking = base(item("locking", CheckType.NORMAL, CompareType.EQ, "OK"));
        locking.getChecks().getFirst().setSql("select * from orders for update");
        DataCheckConfig nonSelectMain = base(item("call", CheckType.NORMAL, CompareType.EQ, "OK"));
        nonSelectMain
                .getChecks()
                .getFirst()
                .setSql("with source as (select 1) call mutate_orders()");

        assertInvalid(mutatingCte);
        assertInvalid(selectInto);
        assertInvalid(locking);
        assertInvalid(nonSelectMain);
    }

    @Test
    void namesVariablesAndBoundsMustBeValidAndUnique() {
        DataCheckItemConfig first = item("same", CheckType.NORMAL, CompareType.EQ, "OK");
        first.setVariable("value");
        DataCheckItemConfig second = item("same", CheckType.NORMAL, CompareType.EQ, "OK");
        second.setVariable("value");
        assertInvalid(base(first, second));

        DataCheckConfig timeout = base(item("one", CheckType.NORMAL, CompareType.EQ, "OK"));
        timeout.setQueryTimeoutSeconds(0);
        assertInvalid(timeout);

        DataCheckItemConfig dynamicOutput = item("one", CheckType.NORMAL, CompareType.EQ, "OK");
        dynamicOutput.setVariable("${output}");
        assertInvalid(base(dynamicOutput));
    }

    @Test
    void typeSpecificComparisonValuesMustNotBeIgnored() {
        DataCheckItemConfig count = item("count", CheckType.COUNT, CompareType.EQ, "not-number");
        assertInvalid(base(count));

        DataCheckItemConfig between = item("between", CheckType.COUNT, CompareType.BETWEEN, null);
        between.setMinValue(5);
        between.setMaxValue(1);
        assertInvalid(base(between));

        DataCheckItemConfig exists = item("exists", CheckType.EXISTS, CompareType.GT, true);
        assertInvalid(base(exists));

        DataCheckItemConfig empty = item("empty", CheckType.EMPTY, CompareType.EQ, 0);
        assertInvalid(base(empty));
    }

    @Test
    void notifyHandlerRequiresSingleValidConfigAndKnownType() {
        DataCheckConfig config = base(item("one", CheckType.NORMAL, CompareType.EQ, "OK"));
        config.setHandlers(List.of(handler("NOTIFY", Map.of("notifyConfigId", 0))));
        assertInvalid(config);

        config = base(item("one", CheckType.NORMAL, CompareType.EQ, "OK"));
        config.setHandlers(List.of(handler("UNKNOWN", Map.of())));
        assertInvalid(config);

        config = base(item("one", CheckType.NORMAL, CompareType.EQ, "OK"));
        config.setHandlers(List.of(handler("VARIABLE", Map.of()), handler("variable", Map.of())));
        assertInvalid(config);
    }

    private DataCheckConfig base(DataCheckItemConfig... items) {
        DataCheckConfig config = new DataCheckConfig();
        config.setResourceId(12L);
        config.setChecks(List.of(items));
        return config;
    }

    private DataCheckItemConfig item(
            String name, CheckType type, CompareType compareType, Object expected) {
        DataCheckItemConfig item = new DataCheckItemConfig();
        item.setName(name);
        item.setSql("select state from orders");
        item.setType(type);
        item.setCompareType(compareType);
        item.setExpected(expected);
        item.setLevel(CheckLevel.ERROR);
        return item;
    }

    private HandlerConfig handler(String type, Map<String, Object> params) {
        HandlerConfig handler = new HandlerConfig();
        handler.setType(type);
        handler.setParams(params);
        return handler;
    }

    private void assertInvalid(DataCheckConfig config) {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }
}
