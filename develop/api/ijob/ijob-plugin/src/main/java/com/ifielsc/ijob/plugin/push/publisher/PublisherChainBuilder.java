// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.plugin.push.FutureWindow;
import com.ifielsc.ijob.plugin.push.compress.CompressionService;
import com.ifielsc.ijob.plugin.push.retry.RetryExecutor;
import com.ifielsc.ijob.plugin.push.retry.RetryPolicy;
import com.ifielsc.ijob.plugin.push.serializer.MessageSerializer;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PublisherChainBuilder {
    private final RetryExecutor retryExecutor;

    public PushPublisher build(
            PushPublisher target,
            MessageSerializer serializer,
            CompressionService compression,
            RetryPolicy retryPolicy,
            int maxInflight,
            int publishTimeoutSeconds) {
        return build(
                target,
                serializer,
                compression,
                retryPolicy,
                maxInflight,
                publishTimeoutSeconds,
                PayloadQuota.unlimited());
    }

    public PushPublisher build(
            PushPublisher target,
            MessageSerializer serializer,
            CompressionService compression,
            RetryPolicy retryPolicy,
            int maxInflight,
            int publishTimeoutSeconds,
            PayloadQuota quota) {
        return build(
                target,
                serializer,
                compression,
                retryPolicy,
                maxInflight,
                publishTimeoutSeconds,
                quota,
                new PublishMetrics());
    }

    public PushPublisher build(
            PushPublisher target,
            MessageSerializer serializer,
            CompressionService compression,
            RetryPolicy retryPolicy,
            int maxInflight,
            int publishTimeoutSeconds,
            PayloadQuota quota,
            PublishMetrics metrics) {
        PushPublisher publisher = target;

        // Retry only the final send; serialization, compression and quota accounting run once.
        publisher = new RetryPublisher(publisher, retryExecutor, retryPolicy);

        publisher = new PayloadLimitPublisher(publisher, quota);

        if (compression != null) {
            publisher = new CompressPublisher(publisher, compression, 1024 * 1024);
        }

        publisher = new SerializePublisher(publisher, serializer);

        // Track the final retry outcome in the asynchronous publication window.
        publisher =
                new AsyncPublisher(
                        publisher,
                        new FutureWindow(maxInflight, publishTimeoutSeconds, TimeUnit.SECONDS));

        publisher = new MetricsPublisher(publisher, metrics);

        return publisher;
    }
}
