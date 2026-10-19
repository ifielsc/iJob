package com.ifielsc.ijob.core.management;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.core.error.CoreMessage;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BatchDeleteValidator {
    public static final int MAX_BATCH_SIZE = 100;

    private BatchDeleteValidator() {}

    public static List<Long> validate(BatchDeleteRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw paramError(CoreMessage.BATCH_DELETION_IDS_IS_REQUIRED);
        }
        List<Long> ids = request.getIds();
        if (ids.size() > MAX_BATCH_SIZE) {
            throw paramError(CoreMessage.A_BATCH_DELETION_MUST_NOT_EXCEED_100_ITEMS);
        }
        Set<Long> uniqueIds = new HashSet<>(ids.size());
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw paramError(CoreMessage.BATCH_DELETION_IDS_MUST_BE_GREATER_THAN_ZERO);
            }
            if (!uniqueIds.add(id)) {
                throw paramError(CoreMessage.BATCH_DELETION_IDS_MUST_BE_UNIQUE);
            }
        }
        return uniqueIds.stream().sorted().toList();
    }

    public static BatchDeleteRequest single(Long id) {
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(Collections.singletonList(id));
        return request;
    }

    private static BusinessException paramError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
