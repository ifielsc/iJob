package com.ifelio.ijob.core.workbench;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.RuntimeRole;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.workbench.WorkbenchDTO;
import com.ifelio.ijob.contract.workbench.WorkbenchFailureDTO;
import com.ifelio.ijob.contract.workbench.WorkbenchQuery;
import com.ifelio.ijob.infra.mapper.workbench.WorkbenchMapper;
import com.ifelio.ijob.infra.mapper.workbench.WorkbenchStatusCount;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkbenchService {
    private final WorkbenchMapper mapper;
    private final AppProperties properties;

    public WorkbenchService(WorkbenchMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public WorkbenchDTO query(WorkbenchQuery requested, CurrentUserResponse user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        WorkbenchQuery query = resolve(requested, now);
        Map<String, String> statuses = emptyStatuses(false);
        merge(statuses, mapper.selectWindowStatuses(query));
        long total =
                statuses.values().stream().mapToLong(Long::parseLong).reduce(0, Math::addExact);
        long success = Long.parseLong(statuses.get("SUCCESS"));
        long denominator =
                Math.addExact(
                        success,
                        Math.addExact(
                                Long.parseLong(statuses.get("FAILED")),
                                Long.parseLong(statuses.get("TIMED_OUT"))));
        String rate =
                denominator == 0
                        ? null
                        : BigDecimal.valueOf(success)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                                .toPlainString();
        Map<String, String> active = emptyStatuses(true);
        merge(active, mapper.selectCurrentActiveRoots());
        boolean approvals = user.getPermissions().contains(AccountPermission.APPROVAL_DECIDE);
        String pending =
                approvals ? Long.toString(mapper.countPendingApprovals(user.getLoginId())) : null;
        List<WorkbenchFailureDTO> failures;
        PageHelper.startPage(1, 5, false);
        try {
            failures = mapper.selectRecentFailures(query);
        } finally {
            PageHelper.clearPage();
        }
        var runtime = properties.getRuntime();
        var roles = runtime.getRoles();
        return new WorkbenchDTO(
                query.getFrom(),
                query.getTo(),
                now,
                "UTC",
                properties.getSchedule().getZoneId(),
                Long.toString(total),
                statuses,
                Long.toString(denominator),
                rate,
                active,
                approvals,
                pending,
                failures,
                new WorkbenchDTO.NodeConfiguration(
                        runtime.getMode().name(),
                        roles.stream().map(Enum::name).sorted().toList(),
                        roles.contains(RuntimeRole.WORKER),
                        roles.contains(RuntimeRole.SCHEDULER),
                        properties.getSchedule().isEnabled(),
                        "UNKNOWN"));
    }

    static WorkbenchQuery resolve(WorkbenchQuery requested, LocalDateTime now) {
        WorkbenchQuery query = new WorkbenchQuery();
        query.setFrom(requested == null ? null : requested.getFrom());
        query.setTo(requested == null ? null : requested.getTo());
        if (query.getFrom() == null && query.getTo() == null) {
            query.setFrom(now.minusHours(24));
            query.setTo(now);
        } else if (query.getFrom() == null || query.getTo() == null) {
            throw parameter("from time and to time must be provided together");
        }
        if (!query.getTo().isAfter(query.getFrom())) {
            throw parameter("to time must be after from time");
        }
        if (Duration.between(query.getFrom(), query.getTo()).compareTo(Duration.ofDays(31)) > 0) {
            throw parameter("query time range cannot exceed 31 days");
        }
        return query;
    }

    private static BusinessException parameter(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    private static Map<String, String> emptyStatuses(boolean activeOnly) {
        Map<String, String> result = new LinkedHashMap<>();
        for (InstanceStatus status : InstanceStatus.values()) {
            if (!activeOnly || !status.isFinished()) result.put(status.name(), "0");
        }
        return result;
    }

    private static void merge(Map<String, String> target, List<WorkbenchStatusCount> rows) {
        for (WorkbenchStatusCount row : rows) {
            // Retain unknown persisted codes in totals instead of silently dropping them.
            target.put(row.getStatus(), Long.toString(row.getCount()));
        }
    }
}
