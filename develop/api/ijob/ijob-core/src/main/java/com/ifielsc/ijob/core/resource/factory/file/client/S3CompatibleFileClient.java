// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import com.ifielsc.ijob.core.resource.factory.file.FileResourceConfig;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class S3CompatibleFileClient extends AbstractFileClient {
    private static final int MULTIPART_PART_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MULTIPART_PARTS = 10_000;

    private final S3Client client;
    private final String bucket;

    public S3CompatibleFileClient(FileResourceConfig config) {
        this(buildClient(config), config.getBucket(), config.getBasePath());
    }

    S3CompatibleFileClient(S3Client client, String bucket, String basePath) {
        super(basePath);
        this.client = client;
        this.bucket = bucket;
    }

    private static S3Client buildClient(FileResourceConfig config) {
        AwsCredentials credentials =
                hasText(config.getSessionToken())
                        ? AwsSessionCredentials.create(
                                config.getAccessKey(),
                                config.getSecretKey(),
                                config.getSessionToken())
                        : AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey());
        var builder =
                S3Client.builder()
                        .region(Region.of(config.getRegion()))
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(config.isPathStyleAccess())
                                        .build())
                        .httpClientBuilder(
                                UrlConnectionHttpClient.builder()
                                        .connectionTimeout(Duration.ofMillis(config.getTimeout()))
                                        .socketTimeout(Duration.ofMillis(config.getTimeout())));
        if (hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        return builder.build();
    }

    @Override
    public List<FileInfo> list(String path) throws IOException {
        return list(path, 100_000);
    }

    @Override
    public List<FileInfo> list(String path, int maxEntries) throws IOException {
        String prefix = directoryPrefix(key(path));
        Map<String, FileInfo> result = new LinkedHashMap<>();
        String continuationToken = null;
        int scannedEntries = 0;
        java.util.Set<String> seenTokens = new java.util.HashSet<>();
        try {
            do {
                ListObjectsV2Response response =
                        client.listObjectsV2(
                                ListObjectsV2Request.builder()
                                        .bucket(bucket)
                                        .prefix(prefix)
                                        .delimiter("/")
                                        .maxKeys(
                                                Math.min(
                                                        1000,
                                                        Math.max(
                                                                1,
                                                                maxEntries - scannedEntries + 1)))
                                        .continuationToken(continuationToken)
                                        .build());
                for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                    if (++scannedEntries > maxEntries)
                        throw new IOException("Directory entry quota exceeded");
                    String name = childName(prefix, commonPrefix.prefix(), true);
                    if (!name.isBlank()) {
                        result.putIfAbsent(
                                name,
                                FileInfo.builder().name(name).directory(true).size(0).build());
                    }
                }
                for (S3Object object : response.contents()) {
                    if (++scannedEntries > maxEntries)
                        throw new IOException("Directory entry quota exceeded");
                    if (object.key().equals(prefix)) {
                        continue;
                    }
                    String name = childName(prefix, object.key(), false);
                    if (!name.isBlank()) {
                        LocalDateTime lastModified =
                                object.lastModified() == null
                                        ? null
                                        : LocalDateTime.ofInstant(
                                                object.lastModified(), ZoneId.systemDefault());
                        result.put(
                                name,
                                FileInfo.builder()
                                        .name(name)
                                        .directory(false)
                                        .size(object.size() == null ? 0L : object.size())
                                        .lastModified(lastModified)
                                        .build());
                    }
                }
                continuationToken = response.nextContinuationToken();
                if (continuationToken != null
                        && (!seenTokens.add(continuationToken) || seenTokens.size() > 1000)) {
                    throw new IOException("Invalid or excessive directory pagination");
                }
            } while (continuationToken != null);
            return new ArrayList<>(result.values());
        } catch (SdkException exception) {
            throw io("S3 object listing failed", exception);
        }
    }

    @Override
    public InputStream download(String path) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> response =
                    client.getObject(
                            GetObjectRequest.builder().bucket(bucket).key(key(path)).build());
            return response;
        } catch (SdkException exception) {
            throw io("S3 object download failed", exception);
        }
    }

    @Override
    public void upload(String path, InputStream input) throws IOException {
        throw new IOException("S3 upload requires a known content length");
    }

    @Override
    public void upload(String path, InputStream input, long contentLength) throws IOException {
        if (contentLength < 0) {
            throw new IOException("S3 upload content length is invalid");
        }
        if (contentLength > MULTIPART_PART_BYTES) {
            if ((contentLength + MULTIPART_PART_BYTES - 1L) / MULTIPART_PART_BYTES
                    > MAX_MULTIPART_PARTS) {
                throw new IOException("S3 upload exceeds the lightweight multipart limit");
            }
            multipartUpload(key(path), input, contentLength);
            return;
        }
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key(path)).build(),
                    RequestBody.fromInputStream(input, contentLength));
        } catch (SdkException exception) {
            throw io("S3 object upload failed", exception);
        }
    }

    private void multipartUpload(String objectKey, InputStream input, long contentLength)
            throws IOException {
        String uploadId = null;
        try {
            uploadId =
                    client.createMultipartUpload(
                                    CreateMultipartUploadRequest.builder()
                                            .bucket(bucket)
                                            .key(objectKey)
                                            .build())
                            .uploadId();
            List<CompletedPart> completedParts = new ArrayList<>();
            long remaining = contentLength;
            int partNumber = 1;
            while (remaining > 0) {
                int expected = (int) Math.min(MULTIPART_PART_BYTES, remaining);
                byte[] part = readPart(input, expected);
                String etag =
                        client.uploadPart(
                                        UploadPartRequest.builder()
                                                .bucket(bucket)
                                                .key(objectKey)
                                                .uploadId(uploadId)
                                                .partNumber(partNumber)
                                                .contentLength((long) expected)
                                                .build(),
                                        RequestBody.fromBytes(part))
                                .eTag();
                completedParts.add(
                        CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
                remaining -= expected;
                partNumber++;
            }
            if (input.read() != -1) {
                throw new IOException("S3 upload content exceeds declared length");
            }
            client.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .uploadId(uploadId)
                            .multipartUpload(
                                    CompletedMultipartUpload.builder()
                                            .parts(completedParts)
                                            .build())
                            .build());
        } catch (IOException exception) {
            abortMultipart(objectKey, uploadId);
            throw exception;
        } catch (SdkException exception) {
            abortMultipart(objectKey, uploadId);
            throw io("S3 multipart upload failed", exception);
        }
    }

    private byte[] readPart(InputStream input, int expected) throws IOException {
        byte[] part = new byte[expected];
        int offset = 0;
        while (offset < expected) {
            int read = input.read(part, offset, expected - offset);
            if (read < 0) {
                throw new IOException("S3 upload content is shorter than declared length");
            }
            offset += read;
        }
        return part;
    }

    private void abortMultipart(String objectKey, String uploadId) {
        if (uploadId == null) {
            return;
        }
        try {
            client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .uploadId(uploadId)
                            .build());
        } catch (SdkException ignored) {
            // Preserve the original upload failure.
        }
    }

    @Override
    public void mkdirs(String path) {
        // Object stores use prefixes rather than physical directories.
    }

    @Override
    public boolean exists(String path) throws IOException {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key(path)).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw io("S3 object existence check failed", exception);
        } catch (SdkException exception) {
            throw io("S3 object existence check failed", exception);
        }
    }

    @Override
    public void delete(String path) throws IOException {
        String objectKey = key(path);
        try {
            if (exists(path)) {
                deleteObject(objectKey);
                return;
            }
            deletePrefix(directoryPrefix(objectKey));
        } catch (SdkException exception) {
            throw io("S3 object deletion failed", exception);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private void deletePrefix(String prefix) {
        List<String> keys = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsV2Response response =
                    client.listObjectsV2(
                            ListObjectsV2Request.builder()
                                    .bucket(bucket)
                                    .prefix(prefix)
                                    .continuationToken(continuationToken)
                                    .build());
            response.contents().stream().map(S3Object::key).forEach(keys::add);
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
        keys.forEach(this::deleteObject);
    }

    private void deleteObject(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    private String key(String path) {
        String resolved = resolve(path);
        return "/".equals(resolved) ? "" : resolved.substring(1);
    }

    private String directoryPrefix(String value) {
        return value.isEmpty() || value.endsWith("/") ? value : value + "/";
    }

    private String childName(String parentPrefix, String childKey, boolean directory) {
        String relative = childKey.substring(parentPrefix.length());
        if (directory && relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        int separator = relative.indexOf('/');
        return separator < 0 ? relative : relative.substring(0, separator);
    }

    private IOException io(String message, Exception cause) {
        if (cause instanceof S3Exception s3Exception) {
            int status = s3Exception.statusCode();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            return new FileClientException(message, retryable, cause);
        }
        return new IOException(message, cause);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
