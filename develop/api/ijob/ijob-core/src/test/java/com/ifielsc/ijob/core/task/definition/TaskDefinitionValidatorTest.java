package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;

import org.junit.jupiter.api.Test;

class TaskDefinitionValidatorTest {
    @Test
    void shouldRejectRetryOverflowAndExcessiveInterval() {
        TaskUpsertRequest request = new TaskUpsertRequest();
        request.setName("bounded retries");
        request.setPluginType(PluginType.SCRIPT);
        request.setPluginConfig("config");
        request.setRetryTimes(Integer.MAX_VALUE);
        assertThrows(
                BusinessException.class, () -> new TaskDefinitionValidator().validate(request));
        request.setRetryTimes(0);
        request.setRetryInterval(Integer.MAX_VALUE);
        assertThrows(
                BusinessException.class, () -> new TaskDefinitionValidator().validate(request));
    }

    @Test
    void shouldRejectMissingTaskName() {
        TaskUpsertRequest request = new TaskUpsertRequest();
        request.setPluginType(PluginType.SCRIPT);
        request.setPluginConfig("config");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> new TaskDefinitionValidator().validate(request));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
    }
}
