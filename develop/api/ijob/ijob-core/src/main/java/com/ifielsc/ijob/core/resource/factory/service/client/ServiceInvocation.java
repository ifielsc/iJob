// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service.client;

import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;

public interface ServiceInvocation extends AutoCloseable {
    ServiceResponse execute();

    /** Called immediately before the business request can be sent, after authentication. */
    default ServiceResponse executeWithDispatch(Runnable beforeSend) {
        beforeSend.run();
        return execute();
    }

    @Override
    void close();
}
