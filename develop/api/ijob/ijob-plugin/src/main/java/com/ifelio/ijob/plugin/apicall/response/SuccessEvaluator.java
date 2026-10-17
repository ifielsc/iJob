package com.ifelio.ijob.plugin.apicall.response;

import com.ifelio.ijob.core.resource.factory.service.ServiceResponse;
import com.ifelio.ijob.plugin.apicall.SuccessCondition;

public interface SuccessEvaluator {
    boolean evaluate(ServiceResponse response, SuccessCondition condition);
}
