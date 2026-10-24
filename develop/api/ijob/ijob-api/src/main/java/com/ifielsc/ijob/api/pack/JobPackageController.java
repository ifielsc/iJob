// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.api.pack;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.pack.JobPackageDTO;
import com.ifielsc.ijob.core.auth.AuthService;
import com.ifielsc.ijob.core.pack.PackageExporter;
import com.ifielsc.ijob.core.pack.PackageImporter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-packages")
public class JobPackageController {
    private final PackageExporter exporter;
    private final PackageImporter importer;
    private final AuthService auth;

    public JobPackageController(
            PackageExporter exporter, PackageImporter importer, AuthService auth) {
        this.exporter = exporter;
        this.importer = importer;
        this.auth = auth;
    }

    @PostMapping("/export")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(action = "PACKAGE_EXPORT", target = "PACKAGE", external = true)
    public ApiResponse<JobPackageDTO.File> export(
            @RequestBody JobPackageDTO.ExportRequest request) {
        return ApiResponse.success(exporter.export(request));
    }

    @PostMapping("/inspect")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    public ApiResponse<JobPackageDTO.Bundle> inspect(@RequestBody JobPackageDTO.Upload request) {
        return ApiResponse.success(importer.inspect(request.content()));
    }

    @PostMapping("/plans")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(action = "PACKAGE_PLAN", target = "PACKAGE", external = true)
    public ApiResponse<JobPackageDTO.Plan> plan(@RequestBody JobPackageDTO.PlanRequest request) {
        return ApiResponse.success(importer.plan(request, auth.currentUser().getLoginId()));
    }

    @PostMapping("/plans/{id}/apply")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(action = "PACKAGE_APPLY", target = "PACKAGE", external = true)
    public ApiResponse<JobPackageDTO.Plan> apply(@PathVariable String id) {
        return ApiResponse.success(importer.apply(id, auth.currentUser().getLoginId()));
    }

    @GetMapping("/plans/{id}")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    public ApiResponse<JobPackageDTO.Plan> get(@PathVariable String id) {
        return ApiResponse.success(importer.get(id, auth.currentUser().getLoginId()));
    }

    @GetMapping("/records")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    public ApiResponse<List<JobPackageDTO.Record>> records() {
        return ApiResponse.success(importer.records(auth.currentUser().getLoginId()));
    }

    @GetMapping("/plans/{id}/verify")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    public ApiResponse<JobPackageDTO.Verification> verify(@PathVariable String id) {
        return ApiResponse.success(importer.verify(id, auth.currentUser().getLoginId()));
    }

    @GetMapping("/candidates")
    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    public ApiResponse<List<Map<String, String>>> candidates(
            @RequestParam JobPackageDTO.Kind kind, @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.success(importer.candidates(kind, page));
    }
}
