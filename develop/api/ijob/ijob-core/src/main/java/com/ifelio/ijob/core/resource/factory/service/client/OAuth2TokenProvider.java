package com.ifelio.ijob.core.resource.factory.service.client;

public interface OAuth2TokenProvider {
    String getBearerToken();

    default String getBearerToken(ServiceCallCancellation cancellation) {
        cancellation.check();
        return getBearerToken();
    }

    void invalidate();
}
