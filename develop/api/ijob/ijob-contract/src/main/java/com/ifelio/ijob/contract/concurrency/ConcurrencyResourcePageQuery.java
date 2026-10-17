package com.ifelio.ijob.contract.concurrency;

import com.ifelio.ijob.contract.common.BasePageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConcurrencyResourcePageQuery extends BasePageQuery {
    private String keyword;
    private String code;
    private String name;
    private Boolean enabled;
}
