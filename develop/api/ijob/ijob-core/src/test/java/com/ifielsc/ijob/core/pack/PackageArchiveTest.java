// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Bundle;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Entry;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class PackageArchiveTest {
    private final PackageArchive archive = new PackageArchive();

    private Bundle bundle() {
        String key = UUID.randomUUID().toString();
        return new Bundle(
                1,
                "1.0.0",
                "Asia/Shanghai",
                List.of(key),
                List.of(
                        new Entry(
                                key,
                                Kind.TASK,
                                "Example",
                                PackageJson.read(
                                        "{\"name\":\"Example\",\"pluginType\":\"SQL\",\"pluginConfig\":{\"sql\":\"SELECT"
                                            + " 9007199254740993\"}}",
                                        com.fasterxml.jackson.databind.node.ObjectNode.class),
                                Map.of(),
                                List.of(),
                                false)));
    }

    @Test
    void roundTripPreservesBusinessTextAndChecksums() {
        var source = bundle();
        var file = archive.encode(source);
        assertEquals(source, archive.decode(file.content()));
        assertEquals(file.sha256(), PackageJson.hash(Base64.getDecoder().decode(file.content())));
    }

    @Test
    void rejectsUnexpectedArchivePaths() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../escaped.json"));
            zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThrows(
                BusinessException.class,
                () -> archive.decode(Base64.getEncoder().encodeToString(bytes.toByteArray())));
    }

    @Test
    void rejectsUnknownVersionsAndCyclicDependencies() {
        var original = bundle();
        assertThrows(
                BusinessException.class,
                () ->
                        archive.encode(
                                new Bundle(
                                        2, "1.0.0", "UTC", original.roots(), original.objects())));
        var e = original.objects().getFirst();
        e.body().putNull("resourceId");
        var cyclic =
                new Entry(
                        e.key(),
                        e.kind(),
                        e.name(),
                        e.body(),
                        Map.of("/resourceId", e.key()),
                        List.of(),
                        false);
        assertThrows(
                BusinessException.class,
                () ->
                        archive.encode(
                                new Bundle(1, "1.0.0", "UTC", original.roots(), List.of(cyclic))));
    }

    @Test
    void removesSecretsAndEmbeddedCredentialsButRetainsSqlAndBusinessIds() {
        var node =
                PackageJson.read(
                        "{\"password\":\"secret-value\",\"url\":\"https://user:pass@example.invalid/x\",\"sql\":\"SELECT"
                            + " 123 AS customer_id\",\"headers\":{\"Authorization\":\"Bearer"
                            + " test-token\"},\"fields\":[{\"sensitive\":true,\"defaultValue\":\"hidden\"}]}",
                        com.fasterxml.jackson.databind.node.ObjectNode.class);
        var paths = PackageSecrets.strip(node);
        assertEquals(4, paths.size());
        assertTrue(node.path("password").isNull());
        assertFalse(node.toString().contains("hidden"));
        assertEquals("SELECT 123 AS customer_id", node.path("sql").asText());
        assertTrue(PackageSecrets.strip(node.deepCopy()).isEmpty());
    }

    @Test
    void resourceRemappingDoesNotTouchUserPayload() {
        var body =
                PackageJson.read(
                        "{\"pluginType\":\"ETL\",\"pluginConfig\":{\"source\":{\"resourceId\":1},\"target\":{\"resourceId\":2},\"transforms\":[{\"properties\":{\"resourceId\":3}}],\"userPayload\":{\"resourceId\":4}}}",
                        com.fasterxml.jackson.databind.node.ObjectNode.class);
        var paths = PackageReferences.paths(Kind.TASK, body);
        assertEquals(3, paths.size());
        assertFalse(paths.containsKey("/pluginConfig/userPayload/resourceId"));
    }

    @Test
    void malformedAndCredentialBearingPackagesAreRejected() {
        assertThrows(BusinessException.class, () -> archive.decode("not base64"));
        var bundle = bundle();
        bundle.objects().getFirst().body().put("password", "should-not-export");
        assertThrows(BusinessException.class, () -> archive.encode(bundle));
    }

    @Test
    void sensitiveScheduledValuesAreRemovedUsingFieldDefinitions() {
        var body =
                PackageJson.read(
                        "{\"variables\":{\"connectionValue\":\"private-input\",\"region\":\"east\"},\"parameterForm\":{\"fields\":[{\"name\":\"connectionValue\",\"sensitive\":true}]}"
                            + " }",
                        com.fasterxml.jackson.databind.node.ObjectNode.class);
        assertEquals(List.of("/variables/connectionValue"), PackageSecrets.strip(body));
        assertTrue(body.path("variables").path("connectionValue").isNull());
        assertEquals("east", body.path("variables").path("region").asText());
    }

    @Test
    void rejectsNullEntriesAndDuplicateJsonFields() {
        var b = bundle();
        assertThrows(
                BusinessException.class,
                () ->
                        archive.encode(
                                new Bundle(
                                        1,
                                        "1.0.0",
                                        "UTC",
                                        b.roots(),
                                        java.util.Arrays.asList((Entry) null))));
        assertThrows(
                BusinessException.class,
                () ->
                        PackageJson.read(
                                "{\"name\":1,\"name\":2}",
                                com.fasterxml.jackson.databind.node.ObjectNode.class));
    }
}
