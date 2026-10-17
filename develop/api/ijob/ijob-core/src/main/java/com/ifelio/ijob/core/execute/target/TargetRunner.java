package com.ifelio.ijob.core.execute.target;

import com.ifelio.ijob.contract.enums.TargetType;

public interface TargetRunner {
    TargetType supportType();

    void run(Long instanceId);
}
