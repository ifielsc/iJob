package com.ifielsc.ijob.plugin.etl.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.validator.impl.RegexValidator;
import com.ifielsc.ijob.plugin.etl.validator.impl.RequiredValidator;

import org.junit.jupiter.api.Test;

import java.util.List;

class ValidatorChainTest {
    @Test
    void shouldCountEarlierFilteredAndCurrentFailuresBeforeAborting() {
        ValidatorConfig required = new ValidatorConfig();
        required.setType(ValidatorType.REQUIRED);
        required.getProperties().put("fields", List.of("code"));
        ValidatorConfig regex = new ValidatorConfig();
        regex.setType(ValidatorType.REGEX);
        regex.setRuleId("code-format");
        regex.setOnFailure(ValidationFailurePolicy.FAIL_TASK);
        regex.getProperties().put("field", "code");
        regex.getProperties().put("pattern", "[A-Z]{2}");
        ValidatorChain chain =
                new ValidatorChain(
                        List.of(required, regex),
                        new ValidatorFactory(
                                List.of(new RequiredValidator(), new RegexValidator())));
        EtlContext context =
                new EtlContext(new TaskContext(1L, null, null, null, null), new EtlTaskConfig());
        context.setValidateErrorCount(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> chain.validate(List.of(record(null), record("bad"), record("OK")), context));

        assertEquals(5, context.getValidateErrorCount());
    }

    @Test
    void shouldPreserveFailuresFromEarlierRules() {
        ValidatorConfig required = new ValidatorConfig();
        required.setType(ValidatorType.REQUIRED);
        required.getProperties().put("fields", List.of("code"));
        ValidatorConfig regex = new ValidatorConfig();
        regex.setType(ValidatorType.REGEX);
        regex.getProperties().put("field", "code");
        regex.getProperties().put("pattern", "[A-Z]{2}");
        ValidatorChain chain =
                new ValidatorChain(
                        List.of(required, regex),
                        new ValidatorFactory(
                                List.of(new RequiredValidator(), new RegexValidator())));

        List<ValidationResult> results = chain.validate(List.of(record(null), record("bad")));

        assertEquals(2, results.size());
        assertFalse(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
    }

    private DataRecord record(String code) {
        DataRecord record = new DataRecord();
        record.put("code", code);
        return record;
    }
}
