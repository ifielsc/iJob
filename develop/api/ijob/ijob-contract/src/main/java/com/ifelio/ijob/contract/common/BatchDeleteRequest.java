package com.ifelio.ijob.contract.common;

import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteRequest {
    private List<Long> ids;
}
