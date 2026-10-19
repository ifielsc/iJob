package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.monitor.client.MonitorClient;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ResourceResolverTest {
    @Test
    void shouldPropagateLeaseValidationFailure() {
        StubResourceRuntimeService runtime =
                new StubResourceRuntimeService(new NoOpMonitorClient());
        runtime.failure = new BusinessException(ResultCode.CONFLICT, "Resource is disabled");
        ResourceResolver resolver = new ResourceResolver(runtime);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> resolver.leaseMonitorClient(7L));

        assertEquals("Resource is disabled", exception.getMessage());
    }

    @Test
    void shouldLeaseDedicatedMonitorClientAndReleaseOnce() {
        NoOpMonitorClient client = new NoOpMonitorClient();
        StubResourceRuntimeService runtime = new StubResourceRuntimeService(client);
        ResourceResolver resolver = new ResourceResolver(runtime);

        ResourceClientLease<MonitorClient> lease = resolver.leaseMonitorClient(8L);

        assertSame(client, lease.getClient());
        assertEquals(8L, runtime.resourceId);
        assertEquals(ResourceType.MONITOR_SERVER, runtime.expectedType);
        lease.close();
        lease.close();
        assertEquals(1, client.closeCount.get());
    }

    private static class StubResourceRuntimeService extends ResourceRuntimeService {
        private final MonitorClient client;
        private Long resourceId;
        private ResourceType expectedType;
        private BusinessException failure;

        StubResourceRuntimeService(MonitorClient client) {
            super(null, null, List.of());
            this.client = client;
        }

        @Override
        public <T> ResourceClientLease<T> leaseClient(
                Long id, ResourceType type, Class<T> clientType) {
            resourceId = id;
            expectedType = type;
            if (failure != null) {
                throw failure;
            }
            return new ResourceClientLease<>(clientType.cast(client), client::close);
        }
    }

    private static class NoOpMonitorClient implements MonitorClient {
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public boolean ping() {
            return true;
        }

        @Override
        public com.ifielsc.ijob.contract.monitor.AgentInfo getAgentInfo() {
            return null;
        }

        @Override
        public com.ifielsc.ijob.contract.monitor.MonitorReport collect(
                com.ifielsc.ijob.contract.monitor.MonitorCollectRequest request) {
            return null;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}
