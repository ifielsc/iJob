package com.ifielsc.ijob.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void successShouldExposeStandardSuccessContract() {
        Object data = new Object();

        ApiResponse<Object> response = ApiResponse.success(data);

        assertTrue(response.isSuccess());
        assertEquals(ResultCode.SUCCESS.code(), response.getCode());
        assertEquals(ResultCode.SUCCESS.errorCode(), response.getErrorCode());
        assertEquals(ResultCode.SUCCESS.messageKey(), response.getMessageKey());
        assertTrue(response.getMessageArgs().isEmpty());
        assertEquals(ResultCode.SUCCESS.message(), response.getMessage());
        assertNull(response.getTraceId());
        assertSame(data, response.getData());
    }

    @Test
    void emptySuccessShouldExposeStandardSuccessContract() {
        ApiResponse<Void> response = ApiResponse.success();

        assertTrue(response.isSuccess());
        assertEquals(ResultCode.SUCCESS.code(), response.getCode());
        assertEquals(ResultCode.SUCCESS.message(), response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void failureShouldExposeProvidedErrorAndNoData() {
        ApiResponse<Object> response = ApiResponse.fail(ResultCode.CONFLICT, "duplicate task");

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.CONFLICT.code(), response.getCode());
        assertEquals(ResultCode.CONFLICT.errorCode(), response.getErrorCode());
        assertEquals(ResultCode.CONFLICT.messageKey(), response.getMessageKey());
        assertEquals("duplicate task", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void failureShouldUseResultCodeMessageByDefault() {
        ApiResponse<Void> response = ApiResponse.fail(ResultCode.PARAM_ERROR);

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.PARAM_ERROR.code(), response.getCode());
        assertEquals(ResultCode.PARAM_ERROR.message(), response.getMessage());
        assertNull(response.getData());
    }
}
