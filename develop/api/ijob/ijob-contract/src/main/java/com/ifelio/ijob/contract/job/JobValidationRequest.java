package com.ifelio.ijob.contract.job;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobValidationRequest {
    private Long jobId;
    private Long expectedRevision;
    private JobUpsertRequest definition;
}
