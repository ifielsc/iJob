// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.resolver;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.plugin.push.PushMessage;
import com.ifielsc.ijob.plugin.push.factory.PushPublisherFactory;
import com.ifielsc.ijob.plugin.push.publisher.PushPublisher;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

class PushPublisherResolverTest {
    @Test
    void configuredTypeShouldSelectMatchingFactory() {
        StubPublisher publisher = new StubPublisher();
        StubFactory factory = new StubFactory(publisher);
        PushPublisherResolver resolver = new PushPublisherResolver(List.of(factory));
        resolver.init();
        PushTaskPublishConfig config = new PushTaskPublishConfig();
        config.setType(ResourceType.KAFKA);

        assertSame(publisher, resolver.resolve(config));
    }

    @Test
    void missingOrUnsupportedTypeShouldFailBeforeFactory() {
        StubFactory factory = new StubFactory(new StubPublisher());
        PushPublisherResolver resolver = new PushPublisherResolver(List.of(factory));
        resolver.init();
        PushTaskPublishConfig missing = new PushTaskPublishConfig();
        PushTaskPublishConfig unsupported = new PushTaskPublishConfig();
        unsupported.setType(ResourceType.DB);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(missing));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(unsupported));
    }

    private static final class StubFactory implements PushPublisherFactory {
        private final PushPublisher publisher;
        private final AtomicInteger calls = new AtomicInteger();

        private StubFactory(PushPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public ResourceType supportType() {
            return ResourceType.KAFKA;
        }

        @Override
        public PushPublisher create(PushTaskPublishConfig config) {
            calls.incrementAndGet();
            return publisher;
        }
    }

    private static final class StubPublisher implements PushPublisher {
        @Override
        public CompletableFuture<Void> publish(PushMessage message) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
