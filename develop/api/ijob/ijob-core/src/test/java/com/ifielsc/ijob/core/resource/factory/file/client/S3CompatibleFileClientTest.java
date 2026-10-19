// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class S3CompatibleFileClientTest {
    @Test
    void uploadShouldUseConfinedKeyAndKnownContentLength() throws Exception {
        AtomicReference<PutObjectRequest> recordedRequest = new AtomicReference<>();
        AtomicReference<RequestBody> recordedBody = new AtomicReference<>();
        S3Client s3 =
                proxy(
                        (method, arguments) -> {
                            if ("putObject".equals(method)) {
                                recordedRequest.set((PutObjectRequest) arguments[0]);
                                recordedBody.set((RequestBody) arguments[1]);
                                return PutObjectResponse.builder().build();
                            }
                            return defaultValue(method);
                        });
        S3CompatibleFileClient client = new S3CompatibleFileClient(s3, "ijob-files", "/safe-root");
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);

        client.upload("nested/data.txt", new ByteArrayInputStream(content), content.length);

        assertEquals("ijob-files", recordedRequest.get().bucket());
        assertEquals("safe-root/nested/data.txt", recordedRequest.get().key());
        assertEquals(
                "payload",
                new String(
                        recordedBody.get().contentStreamProvider().newStream().readAllBytes(),
                        StandardCharsets.UTF_8));
        assertThrows(
                SecurityException.class,
                () ->
                        client.upload(
                                "../../escape", new ByteArrayInputStream(content), content.length));
    }

    @Test
    void listShouldExposeImmediateObjectsAndVirtualDirectories() throws Exception {
        S3Client s3 =
                proxy(
                        (method, arguments) -> {
                            if ("listObjectsV2".equals(method)) {
                                return ListObjectsV2Response.builder()
                                        .commonPrefixes(
                                                CommonPrefix.builder()
                                                        .prefix("safe-root/incoming/nested/")
                                                        .build())
                                        .contents(
                                                S3Object.builder()
                                                        .key("safe-root/incoming/data.csv")
                                                        .size(12L)
                                                        .lastModified(
                                                                Instant.parse(
                                                                        "2026-01-01T00:00:00Z"))
                                                        .build())
                                        .build();
                            }
                            return defaultValue(method);
                        });
        S3CompatibleFileClient client = new S3CompatibleFileClient(s3, "ijob-files", "/safe-root");

        List<FileInfo> files = client.list("incoming");

        assertEquals(2, files.size());
        FileInfo directory = files.stream().filter(FileInfo::isDirectory).findFirst().orElseThrow();
        FileInfo object =
                files.stream().filter(value -> !value.isDirectory()).findFirst().orElseThrow();
        assertEquals("nested", directory.getName());
        assertEquals("data.csv", object.getName());
        assertEquals(12L, object.getSize());
        assertFalse(object.isDirectory());
        assertTrue(object.getLastModified() != null);
    }

    @Test
    void largeUploadShouldUseBoundedMultipartSequence() throws Exception {
        AtomicInteger uploadedParts = new AtomicInteger();
        AtomicReference<CompleteMultipartUploadRequest> completed = new AtomicReference<>();
        S3Client s3 =
                proxy(
                        (method, arguments) ->
                                switch (method) {
                                    case "createMultipartUpload" ->
                                            CreateMultipartUploadResponse.builder()
                                                    .uploadId("upload-1")
                                                    .build();
                                    case "uploadPart" -> {
                                        UploadPartRequest request =
                                                (UploadPartRequest) arguments[0];
                                        uploadedParts.incrementAndGet();
                                        yield UploadPartResponse.builder()
                                                .eTag("etag-" + request.partNumber())
                                                .build();
                                    }
                                    case "completeMultipartUpload" -> {
                                        completed.set(
                                                (CompleteMultipartUploadRequest) arguments[0]);
                                        yield CompleteMultipartUploadResponse.builder().build();
                                    }
                                    default -> defaultValue(method);
                                });
        S3CompatibleFileClient client = new S3CompatibleFileClient(s3, "ijob-files", "/safe-root");
        byte[] content = new byte[8 * 1024 * 1024 + 3];

        client.upload("large.bin", new ByteArrayInputStream(content), content.length);

        assertEquals(2, uploadedParts.get());
        assertEquals(2, completed.get().multipartUpload().parts().size());
        assertEquals("etag-1", completed.get().multipartUpload().parts().getFirst().eTag());
    }

    @Test
    void uploadShouldRejectObjectsBeyondLightweightMultipartLimit() {
        S3CompatibleFileClient client =
                new S3CompatibleFileClient(
                        proxy((method, arguments) -> defaultValue(method)),
                        "ijob-files",
                        "/safe-root");
        long oversized = 8L * 1024 * 1024 * 10_000 + 1;

        assertThrows(
                java.io.IOException.class,
                () ->
                        client.upload(
                                "too-large.bin", new ByteArrayInputStream(new byte[0]), oversized));
    }

    @Test
    void rejectedListRequestShouldBeNonRetryable() {
        S3Client s3 =
                proxy(
                        (method, arguments) -> {
                            if ("listObjectsV2".equals(method)) {
                                throw S3Exception.builder()
                                        .statusCode(403)
                                        .message("forbidden")
                                        .build();
                            }
                            return defaultValue(method);
                        });
        S3CompatibleFileClient client = new S3CompatibleFileClient(s3, "ijob-files", "/safe-root");

        FileClientException exception =
                assertThrows(FileClientException.class, () -> client.list("incoming"));

        assertFalse(exception.isRetryable());
    }

    private S3Client proxy(Invocation invocation) {
        return (S3Client)
                Proxy.newProxyInstance(
                        S3Client.class.getClassLoader(),
                        new Class<?>[] {S3Client.class},
                        (proxy, method, arguments) ->
                                invocation.invoke(method.getName(), arguments));
    }

    private Object defaultValue(String method) {
        if ("serviceName".equals(method)) {
            return "s3";
        }
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Exception;
    }
}
