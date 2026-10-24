// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.ExportRequest;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Selection;

import org.junit.jupiter.api.Test;

import java.util.List;

class PackageExporterTest {
    @Test
    void rejectsDependencyPublicationDuringExport() {
        ObjectNode job = PackageJson.JSON.createObjectNode();
        job.put("name", "Job");
        job.putArray("nodes")
                .addObject()
                .put("kind", "EXECUTION")
                .put("nodeType", "TASK")
                .put("refId", "2")
                .put("refDefinitionId", 7);
        ObjectNode task = PackageJson.JSON.createObjectNode();
        task.put("name", "Task").put("pluginType", "SCRIPT").put("currentTaskDefinitionId", 7);
        task.putObject("pluginConfig");
        ObjectNode published = task.deepCopy().put("currentTaskDefinitionId", 8);
        PackageObjects objects =
                new PackageObjects(
                        null, null, null, null, null, null, null, null, null, null, null, List.of(),
                        null, null, null, null, null, null, null) {
                    int taskReads;

                    @Override
                    ObjectNode detail(Kind kind, String id) {
                        return switch (kind) {
                            case JOB -> job;
                            case TASK -> taskReads++ == 0 ? task : published;
                            case PARAMETER_BINDING -> PackageJson.JSON.createObjectNode();
                            default -> throw new AssertionError(kind);
                        };
                    }

                    @Override
                    ObjectNode body(Kind kind, ObjectNode detail) {
                        return detail.deepCopy();
                    }
                };
        PackageStore store =
                new PackageStore(null, null, new com.ifielsc.ijob.common.config.AppProperties()) {
                    @Override
                    public String identity(String kind, String id) {
                        return kind + ":" + id;
                    }
                };
        PackageArchive archive =
                new PackageArchive() {
                    @Override
                    public com.ifielsc.ijob.contract.pack.JobPackageDTO.File encode(
                            com.ifielsc.ijob.contract.pack.JobPackageDTO.Bundle bundle) {
                        return fail("A changing dependency must never be exported");
                    }
                };
        var exporter = new PackageExporter(objects, store, archive);
        assertThrows(
                BusinessException.class,
                () ->
                        exporter.export(
                                new ExportRequest(
                                        List.of(new Selection(Kind.JOB, "1")),
                                        true,
                                        false,
                                        false)));
    }
}
