// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service.client;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;

import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class OkHttpServiceInvocation implements ServiceInvocation {
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

    private final Call call;
    private final String failureMessage;
    private final int maxResponseBytes;
    private final long start = System.currentTimeMillis();
    private final AtomicBoolean finished = new AtomicBoolean();

    OkHttpServiceInvocation(Call call, String failureMessage, Integer maxResponseBytes) {
        this.call = call;
        this.failureMessage = failureMessage;
        int resolvedMaxResponseBytes =
                maxResponseBytes == null ? DEFAULT_MAX_RESPONSE_BYTES : maxResponseBytes;
        if (resolvedMaxResponseBytes <= 0 || resolvedMaxResponseBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("maxResponseBytes is invalid");
        }
        this.maxResponseBytes = resolvedMaxResponseBytes;
    }

    @Override
    public ServiceResponse execute() {
        return executeWithDispatch(() -> {});
    }

    @Override
    public ServiceResponse executeWithDispatch(Runnable beforeSend) {
        beforeSend.run();
        try (Response response = call.execute()) {
            ServiceResponse result = new ServiceResponse();
            result.setStatus(response.code());
            if (response.body() != null) {
                result.setBody(readBody(response.body()));
            }
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            response.headers()
                    .toMultimap()
                    .forEach((name, values) -> headers.put(name, String.join(", ", values)));
            result.setHeaders(headers);
            result.setElapsed(System.currentTimeMillis() - start);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, failureMessage, e);
        } finally {
            finished.set(true);
        }
    }

    private String readBody(ResponseBody body) throws IOException {
        long contentLength = body.contentLength();
        if (contentLength > maxResponseBytes) {
            throw responseTooLarge();
        }
        byte[] bytes = body.byteStream().readNBytes(maxResponseBytes + 1);
        if (bytes.length > maxResponseBytes) {
            throw responseTooLarge();
        }
        var mediaType = body.contentType();
        var charset =
                mediaType == null
                        ? StandardCharsets.UTF_8
                        : mediaType.charset(StandardCharsets.UTF_8);
        return new String(bytes, charset);
    }

    private BusinessException responseTooLarge() {
        return BusinessException.localized(
                ResultCode.PARAM_ERROR,
                "service response exceeds maxResponseBytes=" + maxResponseBytes);
    }

    @Override
    public void close() {
        if (!finished.get()) {
            call.cancel();
        }
    }
}
