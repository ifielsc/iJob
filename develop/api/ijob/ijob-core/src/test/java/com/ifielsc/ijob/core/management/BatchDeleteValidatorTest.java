// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class BatchDeleteValidatorTest {
    @Test
    void shouldReturnIdsInStableLockOrder() {
        assertEquals(List.of(3L, 5L, 9L), BatchDeleteValidator.validate(request(9L, 3L, 5L)));
    }

    @Test
    void shouldRejectNullEmptyInvalidDuplicateAndOversizedIds() {
        assertParameterError(() -> BatchDeleteValidator.validate(null));
        assertParameterError(() -> BatchDeleteValidator.validate(new BatchDeleteRequest()));
        assertParameterError(() -> BatchDeleteValidator.validate(request()));
        assertParameterError(() -> BatchDeleteValidator.validate(request(1L, null)));
        assertParameterError(() -> BatchDeleteValidator.validate(request(0L)));
        assertParameterError(() -> BatchDeleteValidator.validate(request(1L, 1L)));

        BatchDeleteRequest oversized = new BatchDeleteRequest();
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 101; id++) {
            ids.add(id);
        }
        oversized.setIds(ids);
        assertParameterError(() -> BatchDeleteValidator.validate(oversized));
    }

    private BatchDeleteRequest request(Long... ids) {
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(Arrays.asList(ids));
        return request;
    }

    private void assertParameterError(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
    }
}
