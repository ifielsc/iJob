// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service.client;

public interface OAuth2TokenProvider {
    String getBearerToken();

    default String getBearerToken(ServiceCallCancellation cancellation) {
        cancellation.check();
        return getBearerToken();
    }

    void invalidate();
}
