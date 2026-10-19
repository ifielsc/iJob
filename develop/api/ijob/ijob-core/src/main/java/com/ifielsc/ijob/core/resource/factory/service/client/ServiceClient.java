package com.ifielsc.ijob.core.resource.factory.service.client;

import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;

public interface ServiceClient extends AutoCloseable {
    ServiceResponse invoke(ServiceRequest request);

    default ServiceInvocation newInvocation(ServiceRequest request) {
        return new ServiceInvocation() {
            @Override
            public ServiceResponse execute() {
                return invoke(request);
            }

            @Override
            public void close() {}
        };
    }

    default boolean testConnection() {
        return false;
    }

    default boolean success(ServiceResponse response) {
        return true;
    }

    @Override
    default void close() {}
}
