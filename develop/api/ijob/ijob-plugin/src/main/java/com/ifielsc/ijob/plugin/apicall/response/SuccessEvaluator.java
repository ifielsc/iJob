// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall.response;

import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.plugin.apicall.SuccessCondition;

public interface SuccessEvaluator {
    boolean evaluate(ServiceResponse response, SuccessCondition condition);
}
