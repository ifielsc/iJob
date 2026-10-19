package com.ifielsc.ijob.core.resource.factory.service.client;

import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** A control request may reach the wire at most once, including protocol-driven follow-ups. */
final class ControlRequestClient {
    private ControlRequestClient() {}

    static OkHttpClient once(OkHttpClient source) {
        var sent = new AtomicBoolean();
        return source.newBuilder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .addNetworkInterceptor(
                        chain -> {
                            // retryOnConnectionFailure alone does not suppress 503 Retry-After: 0.
                            if (!sent.compareAndSet(false, true))
                                throw new IOException("Control request replay is disabled");
                            return chain.proceed(chain.request());
                        })
                .build();
    }
}
