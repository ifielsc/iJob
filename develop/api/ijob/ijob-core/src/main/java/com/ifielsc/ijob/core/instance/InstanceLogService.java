package com.ifielsc.ijob.core.instance;

import com.ifielsc.ijob.contract.enums.LogLevel;
import com.ifielsc.ijob.infra.entity.runtime.InstanceLog;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceLogService {
    private final InstanceLogMapper instanceLogMapper;
    private com.ifielsc.ijob.common.observability.CoordinationObserver observer =
            com.ifielsc.ijob.common.observability.CoordinationObserver.NOOP;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setObserver(com.ifielsc.ijob.common.observability.CoordinationObserver observer) {
        this.observer = observer;
    }

    public void info(Long instanceId, String content) {
        save(instanceId, LogLevel.INFO, content);
    }

    public void warn(Long instanceId, String content) {
        save(instanceId, LogLevel.WARN, content);
    }

    public void error(Long instanceId, String content) {
        save(instanceId, LogLevel.ERROR, content);
    }

    private void save(Long instanceId, LogLevel level, String content) {
        InstanceLog log = new InstanceLog();
        log.setInstanceId(instanceId);
        log.setLogTime(LocalDateTime.now());
        log.setLevel(level);
        log.setContent(content);

        try {
            if (instanceLogMapper.insert(log) != 1) {
                throw new IllegalStateException("Instance log insert did not affect one row");
            }
        } catch (RuntimeException exception) {
            try {
                observer.recordEvent("log", "dropped");
            } catch (RuntimeException ignored) {
                // A failed metrics backend must not change execution semantics either.
            }
            // Diagnostic persistence must not turn an already committed plugin action into a retry.
            InstanceLogService.log.error(
                    "Instance log persistence failed, instanceId={}, level={}, failureType={}",
                    instanceId,
                    level,
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
        }
    }
}
