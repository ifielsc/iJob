package com.ifelio.ijob.contract.job;

import com.ifelio.ijob.contract.enums.DagConditionOperator;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DagConditionExpression {
    private DagConditionOperator op;
    private DagConditionOperand left;
    private DagConditionOperand right;
    private List<DagConditionExpression> operands;
    private DagConditionExpression operand;
}
