package com.ifielsc.ijob.api.job;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.job.JobDetailResponse;
import com.ifielsc.ijob.contract.job.JobListDTO;
import com.ifielsc.ijob.contract.job.JobPageQuery;
import com.ifielsc.ijob.contract.job.JobPublicationDTO;
import com.ifielsc.ijob.contract.job.JobUpsertRequest;
import com.ifielsc.ijob.contract.runtime.ManualTriggerRequest;
import com.ifielsc.ijob.core.job.JobService;
import com.ifielsc.ijob.core.trigger.ManualJobTriggerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    private final com.ifielsc.ijob.core.job.JobDesignService jobDesignService;
    private final ManualJobTriggerService manualJobTriggerService;

    public JobController(
            JobService jobService,
            ManualJobTriggerService manualJobTriggerService,
            com.ifielsc.ijob.core.job.JobDesignService jobDesignService) {
        this.jobService = jobService;
        this.jobDesignService = jobDesignService;
        this.manualJobTriggerService = manualJobTriggerService;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping
    public ApiResponse<PageResult<JobListDTO>> queryPage(JobPageQuery query) {
        return ApiResponse.success(jobService.queryPage(query));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<JobDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(jobDesignService.detail(id, null));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "JOB_CREATE",
            target = "JOB",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<JobPublicationDTO>> create(
            @RequestBody JobUpsertRequest request) {
        JobPublicationDTO publication = jobService.create(request);
        return ResponseEntity.created(URI.create("/api/jobs/" + publication.jobId()))
                .body(ApiResponse.success(publication));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "JOB_UPDATE",
            target = "JOB",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<JobPublicationDTO> update(
            @PathVariable Long id,
            @RequestParam Long expectedRevision,
            @RequestBody JobUpsertRequest request) {
        return ApiResponse.success(jobService.update(id, expectedRevision, request));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "JOB_DELETE",
            target = "JOB",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "JOB_DELETE_BATCH",
            target = "JOB",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        jobService.deleteBatch(request);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.EXECUTION_CONTROL)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "JOB_MANUAL_TRIGGER",
            target = "JOB",
            fields = "",
            accepted = true)
    @PostMapping("/{id}/manual-trigger")
    public ResponseEntity<ApiResponse<String>> manualTrigger(
            @PathVariable Long id, @RequestBody(required = false) ManualTriggerRequest request) {
        Long triggerId = manualJobTriggerService.trigger(id, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/triggers/" + triggerId))
                .body(ApiResponse.success(triggerId.toString()));
    }
}
