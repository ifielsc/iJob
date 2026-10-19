package com.ifielsc.ijob.core.execute.target;

import com.ifielsc.ijob.contract.enums.TargetType;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TargetRunnerFactory {

    private final Map<TargetType, TargetRunner> runnerMap = new EnumMap<>(TargetType.class);

    public TargetRunnerFactory(List<TargetRunner> runners) {
        for (TargetRunner runner : runners) {
            runnerMap.put(runner.supportType(), runner);
        }
    }

    public TargetRunner getRunner(TargetType targetType) {
        TargetRunner runner = runnerMap.get(targetType);
        if (runner == null) {
            throw new IllegalArgumentException("unsupported target type: " + targetType);
        }
        return runner;
    }
}
