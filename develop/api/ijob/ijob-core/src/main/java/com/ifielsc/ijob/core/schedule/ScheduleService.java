// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.schedule;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.schedule.ScheduleDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleEnabledRequest;
import com.ifielsc.ijob.contract.schedule.ScheduleMetadataDTO;
import com.ifielsc.ijob.contract.schedule.SchedulePageQuery;
import com.ifielsc.ijob.contract.schedule.SchedulePreviewDTO;
import com.ifielsc.ijob.contract.schedule.SchedulePreviewItemDTO;
import com.ifielsc.ijob.contract.schedule.SchedulePreviewRequest;
import com.ifielsc.ijob.contract.schedule.ScheduleUpsertRequest;
import com.ifielsc.ijob.core.calendar.CalendarService;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.job.definition.JobAdmissionResolver;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifielsc.ijob.infra.entity.schedule.Schedule;
import com.ifielsc.ijob.infra.entity.schedule.ScheduleMisfireAudit;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMisfireAuditMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ScheduleService {
    private static final int MAX_PREVIEW_COUNT = 50;
    private static final int MAX_CALENDAR_SKIP = 10_000;
    private static final int MAX_INTERVAL_SECONDS = 31_536_000;
    private static final int MAX_MISFIRE_ENUMERATION = 100_000;

    private final ScheduleMapper scheduleMapper;
    private final CalendarService calendarService;
    private final TaskAdmissionResolver taskAdmissionResolver;
    private final JobAdmissionResolver jobAdmissionResolver;
    private final VariableSnapshotService variableSnapshotService;
    private final Clock clock;
    private final ZoneId businessZone;
    private final AppProperties.Schedule scheduleProperties;
    private final ScheduleMisfireAuditMapper misfireAuditMapper;

    @Autowired
    public ScheduleService(
            ScheduleMapper scheduleMapper,
            CalendarService calendarService,
            TaskAdmissionResolver taskAdmissionResolver,
            JobAdmissionResolver jobAdmissionResolver,
            VariableSnapshotService variableSnapshotService,
            ScheduleMisfireAuditMapper misfireAuditMapper,
            AppProperties appProperties) {
        this(
                scheduleMapper,
                calendarService,
                taskAdmissionResolver,
                jobAdmissionResolver,
                variableSnapshotService,
                Clock.systemUTC(),
                ZoneId.of(appProperties.getSchedule().getZoneId()),
                appProperties.getSchedule(),
                misfireAuditMapper);
    }

    public ScheduleService(
            ScheduleMapper scheduleMapper,
            CalendarService calendarService,
            TaskAdmissionResolver taskAdmissionResolver,
            JobAdmissionResolver jobAdmissionResolver,
            VariableSnapshotService variableSnapshotService) {
        this(
                scheduleMapper,
                calendarService,
                taskAdmissionResolver,
                jobAdmissionResolver,
                variableSnapshotService,
                Clock.systemUTC(),
                ZoneId.of("Asia/Shanghai"),
                new AppProperties().getSchedule(),
                null);
    }

    ScheduleService(
            ScheduleMapper scheduleMapper,
            CalendarService calendarService,
            TaskAdmissionResolver taskAdmissionResolver,
            JobAdmissionResolver jobAdmissionResolver,
            VariableSnapshotService variableSnapshotService,
            Clock clock,
            ZoneId businessZone) {
        this(
                scheduleMapper,
                calendarService,
                taskAdmissionResolver,
                jobAdmissionResolver,
                variableSnapshotService,
                clock,
                businessZone,
                new AppProperties().getSchedule(),
                null);
    }

    ScheduleService(
            ScheduleMapper scheduleMapper,
            CalendarService calendarService,
            TaskAdmissionResolver taskAdmissionResolver,
            JobAdmissionResolver jobAdmissionResolver,
            VariableSnapshotService variableSnapshotService,
            Clock clock,
            ZoneId businessZone,
            AppProperties.Schedule scheduleProperties,
            ScheduleMisfireAuditMapper misfireAuditMapper) {
        this.scheduleMapper = scheduleMapper;
        this.calendarService = calendarService;
        this.taskAdmissionResolver = taskAdmissionResolver;
        this.jobAdmissionResolver = jobAdmissionResolver;
        this.variableSnapshotService = variableSnapshotService;
        this.clock = clock;
        this.businessZone = businessZone;
        this.scheduleProperties = scheduleProperties;
        this.misfireAuditMapper = misfireAuditMapper;
    }

    public ScheduleMetadataDTO metadata() {
        return new ScheduleMetadataDTO(
                businessZone.getId(),
                ScheduleMisfirePolicy.valueOf(scheduleProperties.getMisfirePolicy().name()),
                scheduleProperties.getMisfireGrace().getSeconds(),
                scheduleProperties.getMaxCatchupCount(),
                scheduleProperties.getMaxCatchupWindow().getSeconds(),
                MAX_PREVIEW_COUNT,
                MAX_INTERVAL_SECONDS,
                1_000,
                100,
                VariableSnapshotService.MAX_SNAPSHOT_BYTES);
    }

    public PageResult<ScheduleDTO> queryPage(SchedulePageQuery query) {
        SchedulePageQuery resolved = query == null ? new SchedulePageQuery() : query;
        PageInfo<Schedule> pageInfo;
        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        try {
            pageInfo = new PageInfo<>(scheduleMapper.selectList(resolved));
        } finally {
            PageHelper.clearPage();
        }
        PageResult<ScheduleDTO> result = new PageResult<>();
        result.setList(pageInfo.getList().stream().map(this::toDto).toList());
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setPages(pageInfo.getPages());
        return result;
    }

    public ScheduleDTO getDetail(Long scheduleId) {
        return toDto(requireSchedule(scheduleId));
    }

    @Transactional
    public Long create(ScheduleUpsertRequest request) {
        return create(request, false);
    }

    /** Only the package importer uses this path; enabling still requires normal admission. */
    @Transactional
    public Long importDisabled(ScheduleUpsertRequest request) {
        if (request == null || !Boolean.FALSE.equals(request.getEnabled())) {
            throw paramError(CoreMessage.ENABLED_STATE_IS_REQUIRED);
        }
        return create(request, true);
    }

    private Long create(ScheduleUpsertRequest request, boolean importedDisabled) {
        LocalDateTime now = nowInBusinessZone();
        Schedule schedule = toSchedule(request, now);
        if (importedDisabled) {
            if (schedule.getTargetType() == TargetType.TASK) {
                taskAdmissionResolver.lockPublishedConfiguration(schedule.getTargetId());
            } else if (schedule.getTargetType() == TargetType.JOB) {
                jobAdmissionResolver.lockPublishedConfiguration(schedule.getTargetId());
            } else throw paramError(CoreMessage.TARGET_TYPE_IS_REQUIRED);
        } else validateTarget(schedule.getTargetType(), schedule.getTargetId());
        validateCalendar(schedule.getCalendarId());
        initializeNextFireTime(schedule, now);
        convertBusinessFieldsToUtc(schedule);
        schedule.setCreateTime(nowUtc());
        schedule.setUpdateTime(nowUtc());
        requireInserted(scheduleMapper.insert(schedule), schedule.getId());
        log.info(
                "schedule created, id={}, targetType={}, targetId={}, enabled={}",
                schedule.getId(),
                schedule.getTargetType(),
                schedule.getTargetId(),
                schedule.getEnabled());
        return schedule.getId();
    }

    @Transactional
    public void update(Long scheduleId, ScheduleUpsertRequest request) {
        Schedule current = requireSchedule(scheduleId);
        LocalDateTime now = nowInBusinessZone();
        Schedule updated = toSchedule(request, now);
        validateTarget(updated.getTargetType(), updated.getTargetId());
        validateCalendar(updated.getCalendarId());
        updated.setId(current.getId());
        updated.setRevision(current.getRevision());
        updated.setLastFireTime(current.getLastFireTime());
        updated.setCreateTime(current.getCreateTime());
        updated.setUpdateTime(now);
        initializeNextFireTime(updated, now);
        convertBusinessFieldsToUtc(updated);
        updated.setUpdateTime(nowUtc());
        requireOne(scheduleMapper.updateById(updated), "Schedule update failed");
        log.info(
                "schedule updated, id={}, targetType={}, targetId={}, enabled={}",
                scheduleId,
                updated.getTargetType(),
                updated.getTargetId(),
                updated.getEnabled());
    }

    @Transactional
    public void updateEnabled(Long scheduleId, ScheduleEnabledRequest request) {
        if (request == null || request.getEnabled() == null) {
            throw paramError(CoreMessage.ENABLED_STATE_IS_REQUIRED);
        }
        Schedule schedule = requireSchedule(scheduleId);
        boolean enabled = request.getEnabled();
        if (enabled) {
            validateTarget(schedule.getTargetType(), schedule.getTargetId());
            validateStoredSchedule(schedule);
            validateCalendar(schedule.getCalendarId());
            Schedule businessSchedule = toBusinessSchedule(schedule);
            LocalDateTime next =
                    calculateFirstExecutableFireTime(businessSchedule, nowInBusinessZone());
            schedule.setNextFireTime(toUtc(next));
            if (schedule.getNextFireTime() == null) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Schedule has no future fire time");
            }
        } else {
            schedule.setNextFireTime(null);
        }
        schedule.setEnabled(enabled);
        schedule.setUpdateTime(nowUtc());
        requireOne(
                scheduleMapper.updateEnabledById(schedule), "Schedule enabled state update failed");
        log.info("schedule enabled state updated, id={}, enabled={}", scheduleId, enabled);
    }

    @Transactional
    public void delete(Long scheduleId) {
        deleteIds(BatchDeleteValidator.validate(BatchDeleteValidator.single(scheduleId)));
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        deleteIds(BatchDeleteValidator.validate(request));
    }

    public SchedulePreviewDTO preview(SchedulePreviewRequest request) {
        if (request == null) {
            throw paramError(CoreMessage.PREVIEW_REQUEST_IS_REQUIRED);
        }
        int count = request.getCount() == null ? 10 : request.getCount();
        if (count < 1 || count > MAX_PREVIEW_COUNT) {
            throw paramError(CoreMessage.PREVIEW_COUNT_MUST_BE_BETWEEN_1_AND_50);
        }
        Schedule schedule = toPreviewSchedule(request);
        validateCalendar(schedule.getCalendarId());
        LocalDateTime cursor =
                request.getFromTime() == null ? nowInBusinessZone() : request.getFromTime();
        List<SchedulePreviewItemDTO> items = new ArrayList<>(count);
        LocalDateTime fireTime = calculateFirstRawFireTime(schedule, cursor);
        while (fireTime != null && items.size() < count) {
            boolean allowed =
                    schedule.getCalendarId() == null
                            || calendarService.allowExecute(schedule.getCalendarId(), fireTime);
            items.add(new SchedulePreviewItemDTO(fireTime, allowed));
            fireTime = calculateFollowingRawFireTime(schedule, fireTime);
        }
        SchedulePreviewDTO result = new SchedulePreviewDTO();
        result.setItems(List.copyOf(items));
        return result;
    }

    public List<Long> listDueScheduleIds(int limit) {
        if (limit < 1 || limit > 1_000) {
            throw paramError(CoreMessage.SCAN_BATCH_SIZE_MUST_BE_BETWEEN_1_AND_1000);
        }
        return scheduleMapper.selectIdsForTrigger(limit);
    }

    public Schedule findScheduleForTrigger(Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw paramError(CoreMessage.SCHEDULE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        return scheduleMapper.selectById(scheduleId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Schedule claimDueSchedule(Long scheduleId, String fireClaimToken) {
        if (scheduleId == null || scheduleId <= 0) {
            throw paramError(CoreMessage.SCHEDULE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        if (fireClaimToken == null || fireClaimToken.isBlank()) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Schedule fire claim token is required");
        }
        if (scheduleMapper.selectDueForTriggerForUpdate(scheduleId) == null
                || scheduleMapper.claimDueForTrigger(scheduleId, fireClaimToken) != 1) {
            return null;
        }
        Schedule claimed = scheduleMapper.selectByFireClaimToken(fireClaimToken);
        if (claimed == null || !scheduleId.equals(claimed.getId())) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Schedule fire claim was lost");
        }
        return claimed;
    }

    public List<ScheduleDTO> listScheduleDetailsByTargetId(TargetType targetType, Long targetId) {
        List<Schedule> schedules = scheduleMapper.selectListByTargetId(targetType, targetId);
        return schedules.stream().map(this::toDto).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markClaimedFireProcessed(
            Schedule schedule, LocalDateTime expectedNextFireTime, String fireClaimToken) {
        schedule.setLastFireTime(expectedNextFireTime);
        LocalDateTime next = calculateFollowingPersistedFireTime(schedule, expectedNextFireTime);
        schedule.setNextFireTime(next);
        if (next == null) {
            schedule.setEnabled(false);
        }
        requireOne(
                scheduleMapper.updateFireTimeIfClaimed(
                        schedule, expectedNextFireTime, fireClaimToken),
                "Schedule fire claim completion failed");
    }

    public ScheduleFirePlan planClaimedFire(Schedule schedule, int triggerBudget) {
        if (schedule == null || schedule.getNextFireTime() == null) {
            throw new IllegalArgumentException("Claimed schedule and next fire time are required");
        }
        if (triggerBudget < 1) {
            throw new IllegalArgumentException("Schedule trigger budget must be positive");
        }
        LocalDateTime now = nowUtc();
        LocalDateTime firstDue = schedule.getNextFireTime();
        ScheduleMisfirePolicy policy = resolvedPolicy(schedule);
        long graceSeconds = resolvedGraceSeconds(schedule);
        boolean misfire = firstDue.plusSeconds(graceSeconds).isBefore(now);
        if (!misfire) {
            LocalDateTime next = calculateFollowingPersistedFireTime(schedule, firstDue);
            List<LocalDateTime> fires =
                    isFireAllowed(schedule, firstDue) ? List.of(firstDue) : List.of();
            return new ScheduleFirePlan(
                    policy,
                    false,
                    firstDue,
                    firstDue,
                    fires,
                    next,
                    1,
                    fires.isEmpty() ? 1 : 0,
                    false);
        }

        LocalDateTime windowStart = now.minus(scheduleProperties.getMaxCatchupWindow());
        boolean countCapped = firstDue.isBefore(windowStart);
        LocalDateTime cursor = fastForwardPersisted(schedule, firstDue, windowStart);
        List<LocalDateTime> due = new ArrayList<>();
        int enumerated = 0;
        while (cursor != null && !cursor.isAfter(now) && enumerated < MAX_MISFIRE_ENUMERATION) {
            enumerated++;
            if (isFireAllowed(schedule, cursor)) {
                due.add(cursor);
            }
            cursor = calculateFollowingPersistedFireTime(schedule, cursor);
        }
        if (cursor != null && !cursor.isAfter(now)) {
            countCapped = true;
            cursor = firstPersistedAfter(schedule, now);
        }

        int perScheduleLimit = resolvedCatchupCount(schedule);
        int limit = Math.min(triggerBudget, perScheduleLimit);
        List<LocalDateTime> fires =
                switch (policy) {
                    case SKIP -> List.of();
                    case FIRE_ONCE -> due.isEmpty() ? List.of() : List.of(due.getLast());
                    case CATCH_UP_BOUNDED ->
                            List.copyOf(due.subList(0, Math.min(limit, due.size())));
                };
        int skipped = Math.max(0, due.size() - fires.size());
        LocalDateTime lastDue = due.isEmpty() ? firstDue : due.getLast();
        return new ScheduleFirePlan(
                policy, true, firstDue, lastDue, fires, cursor, due.size(), skipped, countCapped);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeClaimedFire(
            Schedule schedule,
            LocalDateTime expectedNextFireTime,
            String fireClaimToken,
            ScheduleFirePlan plan) {
        if (!plan.fireTimes().isEmpty()) {
            schedule.setLastFireTime(plan.fireTimes().getLast());
        }
        schedule.setNextFireTime(plan.nextFireTime());
        if (plan.nextFireTime() == null) {
            schedule.setEnabled(false);
        }
        requireOne(
                scheduleMapper.updateFireTimeIfClaimed(
                        schedule, expectedNextFireTime, fireClaimToken),
                "Schedule fire claim completion failed");
        if (plan.misfire()) {
            recordMisfire(schedule, fireClaimToken, plan);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void skipClaimedFire(
            Schedule schedule, LocalDateTime expectedNextFireTime, String fireClaimToken) {
        LocalDateTime next = calculateFollowingPersistedFireTime(schedule, expectedNextFireTime);
        schedule.setNextFireTime(next);
        if (next == null) {
            schedule.setEnabled(false);
        }
        requireOne(
                scheduleMapper.updateFireTimeIfClaimed(
                        schedule, expectedNextFireTime, fireClaimToken),
                "Schedule fire claim completion failed");
    }

    private Schedule toSchedule(ScheduleUpsertRequest request, LocalDateTime now) {
        validateRequest(request);
        Schedule schedule = new Schedule();
        schedule.setTargetType(request.getTargetType());
        schedule.setTargetId(request.getTargetId());
        schedule.setScheduleType(request.getScheduleType());
        schedule.setCronExpr(normalizeCron(request.getCronExpr()));
        schedule.setIntervalSec(request.getIntervalSec());
        schedule.setCalendarId(request.getCalendarId());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        schedule.setMisfirePolicy(request.getMisfirePolicy());
        schedule.setMisfireGraceSec(request.getMisfireGraceSec());
        schedule.setMaxCatchupCount(request.getMaxCatchupCount());
        schedule.setMaxQueueDelaySec(request.getMaxQueueDelaySec());
        schedule.setVariables(
                variableSnapshotService.serializeUserVariables(
                        request.getVariables(), request.getParameterForm()));
        schedule.setParameterForm(
                variableSnapshotService.serializeForm(request.getParameterForm()));
        schedule.setUpdateTime(now);
        return schedule;
    }

    private void deleteIds(List<Long> ids) {
        List<Long> existing = scheduleMapper.selectIdsForUpdate(ids);
        if (existing == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Schedule batch lock failed");
        }
        if (!existing.equals(ids)) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Some schedules do not exist");
        }
        requireAffected(scheduleMapper.deleteByIds(ids), ids.size(), "Schedule delete conflict");
        log.info("schedules deleted, count={}", ids.size());
    }

    private Schedule toPreviewSchedule(SchedulePreviewRequest request) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(request.getScheduleType());
        schedule.setCronExpr(normalizeCron(request.getCronExpr()));
        schedule.setIntervalSec(request.getIntervalSec());
        schedule.setCalendarId(request.getCalendarId());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        validateStoredSchedule(schedule);
        return schedule;
    }

    private void initializeNextFireTime(Schedule schedule, LocalDateTime now) {
        if (!Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setNextFireTime(null);
            return;
        }
        LocalDateTime next = calculateFirstExecutableFireTime(schedule, now);
        if (next == null) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Schedule has no future fire time");
        }
        schedule.setNextFireTime(next);
    }

    public boolean isFireAllowed(Schedule schedule, LocalDateTime storedFireTime) {
        return schedule.getCalendarId() == null
                || calendarService.allowExecute(
                        schedule.getCalendarId(), toBusiness(storedFireTime));
    }

    private LocalDateTime calculateFollowingPersistedFireTime(
            Schedule schedule, LocalDateTime storedCurrent) {
        Schedule businessSchedule = toBusinessSchedule(schedule);
        return toUtc(
                calculateFollowingExecutableFireTime(businessSchedule, toBusiness(storedCurrent)));
    }

    private LocalDateTime fastForwardPersisted(
            Schedule schedule, LocalDateTime firstDue, LocalDateTime windowStart) {
        if (!firstDue.isBefore(windowStart)) {
            return firstDue;
        }
        if (schedule.getScheduleType() == com.ifielsc.ijob.contract.enums.ScheduleType.ONCE) {
            return firstDue;
        }
        if (schedule.getScheduleType() == com.ifielsc.ijob.contract.enums.ScheduleType.INTERVAL) {
            long interval = schedule.getIntervalSec();
            long seconds = Math.max(0L, Duration.between(firstDue, windowStart).getSeconds());
            long steps = seconds / interval + (seconds % interval == 0 ? 0 : 1);
            LocalDateTime candidate;
            try {
                candidate = firstDue.plusSeconds(Math.multiplyExact(steps, interval));
            } catch (ArithmeticException | java.time.DateTimeException exception) {
                return null;
            }
            if (schedule.getEndTime() != null && candidate.isAfter(schedule.getEndTime())) {
                return null;
            }
            while (candidate != null && !isFireAllowed(schedule, candidate)) {
                candidate = calculateFollowingPersistedFireTime(schedule, candidate);
            }
            return candidate;
        }
        Schedule businessSchedule = toBusinessSchedule(schedule);
        return toUtc(
                calculateFirstExecutableFireTime(
                        businessSchedule, toBusiness(windowStart).minusNanos(1)));
    }

    private LocalDateTime firstPersistedAfter(Schedule schedule, LocalDateTime after) {
        if (schedule.getScheduleType() == com.ifielsc.ijob.contract.enums.ScheduleType.ONCE) {
            return null;
        }
        if (schedule.getScheduleType() == com.ifielsc.ijob.contract.enums.ScheduleType.INTERVAL) {
            return fastForwardPersisted(schedule, schedule.getNextFireTime(), after.plusNanos(1));
        }
        Schedule businessSchedule = toBusinessSchedule(schedule);
        return toUtc(calculateFirstExecutableFireTime(businessSchedule, toBusiness(after)));
    }

    private ScheduleMisfirePolicy resolvedPolicy(Schedule schedule) {
        if (schedule.getMisfirePolicy() != null) {
            return schedule.getMisfirePolicy();
        }
        return ScheduleMisfirePolicy.valueOf(scheduleProperties.getMisfirePolicy().name());
    }

    private long resolvedGraceSeconds(Schedule schedule) {
        return schedule.getMisfireGraceSec() == null
                ? scheduleProperties.getMisfireGrace().toSeconds()
                : schedule.getMisfireGraceSec();
    }

    private int resolvedCatchupCount(Schedule schedule) {
        return schedule.getMaxCatchupCount() == null
                ? scheduleProperties.getMaxCatchupCount()
                : schedule.getMaxCatchupCount();
    }

    private void recordMisfire(Schedule schedule, String fireClaimToken, ScheduleFirePlan plan) {
        if (misfireAuditMapper == null) {
            return;
        }
        ScheduleMisfireAudit audit = new ScheduleMisfireAudit();
        audit.setScheduleId(schedule.getId());
        audit.setFireClaimToken(fireClaimToken);
        audit.setPolicy(plan.policy());
        audit.setObservedAt(nowUtc());
        audit.setFirstDueTime(plan.firstDueTime());
        audit.setLastDueTime(plan.lastDueTime());
        audit.setDueCount(plan.dueCount());
        audit.setCountCapped(plan.countCapped());
        audit.setCreatedCount(plan.fireTimes().size());
        audit.setSkippedCount(plan.skippedCount());
        audit.setCreateTime(nowUtc());
        requireOne(misfireAuditMapper.insert(audit), "Schedule misfire audit creation failed");
    }

    private Schedule toBusinessSchedule(Schedule stored) {
        Schedule business = new Schedule();
        BeanUtils.copyProperties(stored, business);
        business.setStartTime(toBusiness(stored.getStartTime()));
        business.setEndTime(toBusiness(stored.getEndTime()));
        business.setLastFireTime(toBusiness(stored.getLastFireTime()));
        business.setNextFireTime(toBusiness(stored.getNextFireTime()));
        return business;
    }

    private void convertBusinessFieldsToUtc(Schedule schedule) {
        schedule.setStartTime(toUtc(schedule.getStartTime()));
        schedule.setEndTime(toUtc(schedule.getEndTime()));
        schedule.setNextFireTime(toUtc(schedule.getNextFireTime()));
    }

    private LocalDateTime nowInBusinessZone() {
        return LocalDateTime.ofInstant(clock.instant(), businessZone);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private LocalDateTime toUtc(LocalDateTime businessTime) {
        return businessTime == null
                ? null
                : LocalDateTime.ofInstant(
                        businessTime.atZone(businessZone).toInstant(), ZoneOffset.UTC);
    }

    private LocalDateTime toBusiness(LocalDateTime utcTime) {
        return utcTime == null
                ? null
                : LocalDateTime.ofInstant(utcTime.toInstant(ZoneOffset.UTC), businessZone);
    }

    private LocalDateTime calculateFirstExecutableFireTime(Schedule schedule, LocalDateTime after) {
        LocalDateTime candidate = calculateFirstRawFireTime(schedule, after);
        return skipCalendar(schedule, candidate);
    }

    private LocalDateTime calculateFollowingExecutableFireTime(
            Schedule schedule, LocalDateTime current) {
        if (current == null) {
            return null;
        }
        LocalDateTime candidate = calculateFollowingRawFireTime(schedule, current);
        return skipCalendar(schedule, candidate);
    }

    private LocalDateTime skipCalendar(Schedule schedule, LocalDateTime candidate) {
        if (schedule.getCalendarId() == null) {
            return candidate;
        }
        int remaining = MAX_CALENDAR_SKIP;
        LocalDateTime next = candidate;
        while (next != null && remaining-- > 0) {
            if (calendarService.allowExecute(schedule.getCalendarId(), next)) {
                return next;
            }
            next = calculateFollowingRawFireTime(schedule, next);
        }
        if (next != null) {
            log.warn("schedule calendar preview overflow, scheduleId={}", schedule.getId());
        }
        return null;
    }

    private LocalDateTime calculateFirstRawFireTime(Schedule schedule, LocalDateTime after) {
        LocalDateTime start = schedule.getStartTime();
        LocalDateTime candidate =
                switch (schedule.getScheduleType()) {
                    case CRON -> firstCron(schedule, after, start);
                    case INTERVAL ->
                            start != null && start.isAfter(after)
                                    ? start
                                    : after.plusSeconds(schedule.getIntervalSec());
                    case ONCE -> start != null && !start.isBefore(after) ? start : null;
                };
        return applyEndTime(candidate, schedule.getEndTime());
    }

    private LocalDateTime firstCron(Schedule schedule, LocalDateTime after, LocalDateTime start) {
        CronExpression cron = parseCron(schedule.getCronExpr());
        if (start != null && start.isAfter(after)) {
            if (matches(cron, start)) {
                return start;
            }
            return cron.next(start);
        }
        return cron.next(after);
    }

    private LocalDateTime calculateFollowingRawFireTime(Schedule schedule, LocalDateTime current) {
        LocalDateTime candidate =
                switch (schedule.getScheduleType()) {
                    case CRON -> parseCron(schedule.getCronExpr()).next(current);
                    case INTERVAL -> current.plusSeconds(schedule.getIntervalSec());
                    case ONCE -> null;
                };
        return applyEndTime(candidate, schedule.getEndTime());
    }

    private boolean matches(CronExpression cron, LocalDateTime time) {
        return time.equals(cron.next(time.minusSeconds(1)));
    }

    private LocalDateTime applyEndTime(LocalDateTime candidate, LocalDateTime endTime) {
        if (candidate == null || endTime != null && candidate.isAfter(endTime)) {
            return null;
        }
        return candidate;
    }

    public void validateImport(ScheduleUpsertRequest request) {
        validateRequest(request);
        variableSnapshotService.serializeUserVariables(
                request.getVariables(), request.getParameterForm());
    }

    private void validateRequest(ScheduleUpsertRequest request) {
        if (request == null) {
            throw paramError(CoreMessage.REQUEST_BODY_IS_REQUIRED);
        }
        if (request.getTargetType() == null) {
            throw paramError(CoreMessage.TARGET_TYPE_IS_REQUIRED);
        }
        if (request.getTargetId() == null || request.getTargetId() <= 0) {
            throw paramError(CoreMessage.TARGET_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        Schedule schedule = new Schedule();
        schedule.setScheduleType(request.getScheduleType());
        schedule.setCronExpr(normalizeCron(request.getCronExpr()));
        schedule.setIntervalSec(request.getIntervalSec());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        validateStoredSchedule(schedule);
        if (request.getCalendarId() != null && request.getCalendarId() <= 0) {
            throw paramError(CoreMessage.CALENDAR_ID_MUST_BE_GREATER_THAN_ZERO_20);
        }
        if (request.getMisfireGraceSec() != null && request.getMisfireGraceSec() < 0
                || request.getMaxCatchupCount() != null
                        && (request.getMaxCatchupCount() < 1
                                || request.getMaxCatchupCount() > 1_000)
                || request.getMaxQueueDelaySec() != null
                        && (request.getMaxQueueDelaySec() < 1
                                || request.getMaxQueueDelaySec() > MAX_INTERVAL_SECONDS)) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Schedule misfire or queue delay policy is invalid");
        }
    }

    private void validateStoredSchedule(Schedule schedule) {
        if (schedule.getScheduleType() == null) {
            throw paramError(CoreMessage.SCHEDULE_TYPE_IS_REQUIRED);
        }
        if (schedule.getStartTime() != null
                && schedule.getEndTime() != null
                && schedule.getStartTime().isAfter(schedule.getEndTime())) {
            throw paramError(CoreMessage.START_TIME_MUST_NOT_BE_LATER_THAN_END_TIME);
        }
        switch (schedule.getScheduleType()) {
            case CRON -> {
                if (schedule.getCronExpr() == null) {
                    throw paramError(CoreMessage.CRON_EXPRESSION_IS_REQUIRED);
                }
                parseCron(schedule.getCronExpr());
                if (schedule.getIntervalSec() != null) {
                    throw paramError(CoreMessage.CRON_SCHEDULES_MUST_NOT_SPECIFY_INTERVAL_SECONDS);
                }
            }
            case INTERVAL -> {
                Integer interval = schedule.getIntervalSec();
                if (interval == null || interval <= 0 || interval > MAX_INTERVAL_SECONDS) {
                    throw paramError(CoreMessage.INTERVAL_SECONDS_MUST_BE_BETWEEN_1_AND_31536000);
                }
                if (schedule.getCronExpr() != null) {
                    throw paramError(
                            CoreMessage.INTERVAL_SCHEDULES_MUST_NOT_SPECIFY_A_CRON_EXPRESSION);
                }
            }
            case ONCE -> {
                if (schedule.getStartTime() == null) {
                    throw paramError(CoreMessage.ONE_TIME_SCHEDULES_REQUIRE_A_START_TIME);
                }
                if (schedule.getCronExpr() != null || schedule.getIntervalSec() != null) {
                    throw paramError(
                            CoreMessage
                                    .ONE_TIME_SCHEDULES_MUST_NOT_SPECIFY_CRON_OR_INTERVAL_SECONDS);
                }
            }
        }
    }

    private CronExpression parseCron(String expression) {
        try {
            return CronExpression.parse(expression);
        } catch (IllegalArgumentException exception) {
            throw paramError(CoreMessage.CRON_EXPRESSION_IS_INVALID);
        }
    }

    private String normalizeCron(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return expression.trim();
    }

    private void validateCalendar(Long calendarId) {
        if (calendarId == null) {
            return;
        }
        if (calendarId <= 0) {
            throw paramError(CoreMessage.CALENDAR_ID_MUST_BE_GREATER_THAN_ZERO_20);
        }
        if (calendarService.getCalendar(calendarId) == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Calendar not found");
        }
    }

    private void validateTarget(TargetType type, Long targetId) {
        if (type == TargetType.TASK) {
            taskAdmissionResolver.resolve(targetId);
            return;
        }
        if (type == TargetType.JOB) {
            jobAdmissionResolver.resolve(targetId);
            return;
        }
        throw paramError(CoreMessage.TARGET_TYPE_IS_REQUIRED);
    }

    private Schedule requireSchedule(Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw paramError(CoreMessage.SCHEDULE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Schedule not found");
        }
        return schedule;
    }

    private ScheduleDTO toDto(Schedule schedule) {
        ScheduleDTO dto = new ScheduleDTO();
        BeanUtils.copyProperties(schedule, dto, "parameterForm");
        dto.setStartTime(toBusiness(schedule.getStartTime()));
        dto.setEndTime(toBusiness(schedule.getEndTime()));
        dto.setLastFireTime(toBusiness(schedule.getLastFireTime()));
        dto.setNextFireTime(toBusiness(schedule.getNextFireTime()));
        dto.setVariables(variableSnapshotService.deserializeUserVariables(schedule.getVariables()));
        dto.setParameterForm(variableSnapshotService.deserializeForm(schedule.getParameterForm()));
        return dto;
    }

    private void requireInserted(int rows, Long generatedId) {
        if (rows != 1 || generatedId == null || generatedId <= 0) {
            throw BusinessException.localized(ResultCode.SYSTEM_ERROR, "Schedule creation failed");
        }
    }

    private void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT, message);
        }
    }

    private void requireAffected(int rows, int expected, String message) {
        if (rows != expected) {
            throw new BusinessException(ResultCode.CONFLICT, message);
        }
    }

    private BusinessException paramError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
