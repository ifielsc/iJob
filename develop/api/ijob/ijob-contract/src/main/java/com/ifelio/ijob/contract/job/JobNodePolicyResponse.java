package com.ifelio.ijob.contract.job;

import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobNodePolicyResponse {
    private Long id;
    private FailStrategy failStrategy;
    private ExecuteCondition executeCondition;
}
