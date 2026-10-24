// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.calendar;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.calendar.CalendarDetailDTO;
import com.ifielsc.ijob.contract.calendar.CalendarExceptionDTO;
import com.ifielsc.ijob.contract.calendar.CalendarExceptionRequest;
import com.ifielsc.ijob.contract.calendar.CalendarListDTO;
import com.ifielsc.ijob.contract.calendar.CalendarPageQuery;
import com.ifielsc.ijob.contract.calendar.CalendarPreviewDayDTO;
import com.ifielsc.ijob.contract.calendar.CalendarUpsertRequest;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.CalendarDateType;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.infra.entity.calendar.Calendar;
import com.ifielsc.ijob.infra.entity.calendar.CalendarException;
import com.ifielsc.ijob.infra.entity.calendar.WorkdayMask;
import com.ifielsc.ijob.infra.mapper.calendar.CalendarExceptionMapper;
import com.ifielsc.ijob.infra.mapper.calendar.CalendarMapper;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CalendarService {
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_EXCEPTION_COUNT = 2000;

    private final CalendarMapper calendarMapper;
    private final CalendarExceptionMapper exceptionMapper;
    private final ScheduleMapper scheduleMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final LoadingCache<Long, CalendarDefinition> cache;
    private final Clock clock;
    private final ZoneId businessZone;

    public CalendarService(
            CalendarMapper calendarMapper,
            CalendarExceptionMapper exceptionMapper,
            ScheduleMapper scheduleMapper,
            ApplicationEventPublisher eventPublisher) {
        this(
                calendarMapper,
                exceptionMapper,
                scheduleMapper,
                eventPublisher,
                Duration.ofSeconds(5),
                Clock.systemUTC(),
                ZoneId.of("Asia/Shanghai"));
    }

    @Autowired
    public CalendarService(
            CalendarMapper calendarMapper,
            CalendarExceptionMapper exceptionMapper,
            ScheduleMapper scheduleMapper,
            ApplicationEventPublisher eventPublisher,
            AppProperties appProperties) {
        this(
                calendarMapper,
                exceptionMapper,
                scheduleMapper,
                eventPublisher,
                appProperties.getCacheConsistency().getMaxStaleness(),
                Clock.systemUTC(),
                ZoneId.of(appProperties.getSchedule().getZoneId()));
    }

    CalendarService(
            CalendarMapper calendarMapper,
            CalendarExceptionMapper exceptionMapper,
            ScheduleMapper scheduleMapper,
            ApplicationEventPublisher eventPublisher,
            AppProperties appProperties,
            Clock clock) {
        this(
                calendarMapper,
                exceptionMapper,
                scheduleMapper,
                eventPublisher,
                appProperties.getCacheConsistency().getMaxStaleness(),
                clock,
                ZoneId.of(appProperties.getSchedule().getZoneId()));
    }

    private CalendarService(
            CalendarMapper calendarMapper,
            CalendarExceptionMapper exceptionMapper,
            ScheduleMapper scheduleMapper,
            ApplicationEventPublisher eventPublisher,
            Duration maxStaleness,
            Clock clock,
            ZoneId businessZone) {
        this.calendarMapper = calendarMapper;
        this.exceptionMapper = exceptionMapper;
        this.scheduleMapper = scheduleMapper;
        this.eventPublisher = eventPublisher;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(maxStaleness)
                        .build(this::loadDefinition);
    }

    public PageResult<CalendarListDTO> queryPage(CalendarPageQuery query) {
        CalendarPageQuery resolvedQuery = query == null ? new CalendarPageQuery() : query;
        PageInfo<Calendar> pageInfo;
        PageHelper.startPage(resolvedQuery.getPageNo(), resolvedQuery.getPageSize());
        try {
            pageInfo = new PageInfo<>(calendarMapper.selectList(resolvedQuery));
        } finally {
            PageHelper.clearPage();
        }

        PageResult<CalendarListDTO> result = new PageResult<>();
        result.setList(pageInfo.getList().stream().map(this::toListDto).toList());
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setPages(pageInfo.getPages());
        return result;
    }

    public CalendarDetailDTO getDetail(Long calendarId) {
        Calendar calendar = requireCalendar(calendarId);
        List<CalendarException> exceptions = exceptionMapper.selectByCalendarId(calendarId);
        return toDetailDto(calendar, exceptions);
    }

    @Transactional
    public Long create(CalendarUpsertRequest request) {
        List<CalendarExceptionRequest> exceptions = validateRequest(request);
        String name = request.getName().trim();
        ensureNameAvailable(name, null);

        LocalDateTime now = LocalDateTime.now();
        Calendar calendar = toCalendar(request);
        calendar.setName(name);
        calendar.setCreateTime(now);
        calendar.setUpdateTime(now);
        if (calendarMapper.insert(calendar) != 1 || calendar.getId() == null) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, CoreMessage.CALENDAR_CREATION_FAILED);
        }
        insertExceptions(calendar.getId(), exceptions, now);
        log.info("calendar created, id={}, exceptionCount={}", calendar.getId(), exceptions.size());
        return calendar.getId();
    }

    @Transactional
    public void update(Long calendarId, CalendarUpsertRequest request) {
        List<CalendarExceptionRequest> exceptions = validateRequest(request);
        requireCalendar(calendarId);
        String name = request.getName().trim();
        ensureNameAvailable(name, calendarId);

        Calendar calendar = toCalendar(request);
        calendar.setId(calendarId);
        calendar.setName(name);
        calendar.setUpdateTime(LocalDateTime.now());
        if (calendarMapper.updateById(calendar) != 1) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.CALENDAR_UPDATE_FAILED);
        }

        exceptionMapper.deleteByCalendarId(calendarId);
        insertExceptions(calendarId, exceptions, calendar.getUpdateTime());
        eventPublisher.publishEvent(new CalendarCacheInvalidationEvent(calendarId));
        log.info("calendar updated, id={}, exceptionCount={}", calendarId, exceptions.size());
    }

    @Transactional
    public void delete(Long calendarId) {
        deleteIds(BatchDeleteValidator.validate(BatchDeleteValidator.single(calendarId)));
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        deleteIds(BatchDeleteValidator.validate(request));
    }

    public boolean allowExecute(Long calendarId, LocalDateTime time) {
        if (time == null) {
            throw paramError(CoreMessage.EXECUTION_TIME_IS_REQUIRED);
        }
        return allowExecute(calendarId, time.toLocalDate());
    }

    public boolean allowExecute(Long calendarId, LocalDate date) {
        if (calendarId == null) {
            return true;
        }
        requirePositiveId(calendarId);
        if (date == null) {
            throw paramError(CoreMessage.EXECUTION_DATE_IS_REQUIRED);
        }

        CalendarDefinition definition = cache.get(calendarId);
        if (definition == null) {
            return true;
        }

        CalendarDateType exceptionType = definition.exceptions().get(date);
        if (exceptionType != null) {
            return exceptionType == CalendarDateType.WORKDAY;
        }
        return definition.calendar().isWorkday(date);
    }

    public Calendar getCalendar(Long calendarId) {
        if (calendarId == null) {
            return null;
        }
        CalendarDefinition definition = cache.get(requirePositiveId(calendarId));
        return definition == null ? null : definition.calendar();
    }

    public com.ifielsc.ijob.contract.calendar.CalendarContextDTO context() {
        return new com.ifielsc.ijob.contract.calendar.CalendarContextDTO(
                LocalDate.ofInstant(clock.instant(), businessZone), businessZone.getId());
    }

    public List<CalendarPreviewDayDTO> preview(Long calendarId, LocalDate from, Integer days) {
        Long id = requirePositiveId(calendarId);
        int previewDays = days == null ? 14 : days;
        if (previewDays < 1 || previewDays > 366) {
            throw paramError(CoreMessage.PREVIEW_DAYS_MUST_BE_BETWEEN_1_AND_366);
        }
        LocalDate firstDate =
                from == null ? LocalDate.ofInstant(clock.instant(), businessZone) : from;
        CalendarDefinition definition = cache.get(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.CALENDAR_DOES_NOT_EXIST);
        }

        List<CalendarPreviewDayDTO> result = new ArrayList<>(previewDays);
        for (int offset = 0; offset < previewDays; offset++) {
            LocalDate date = firstDate.plusDays(offset);
            CalendarDateType exceptionType = definition.exceptions().get(date);
            CalendarPreviewDayDTO item = new CalendarPreviewDayDTO();
            item.setDate(date);
            item.setExceptionType(exceptionType);
            item.setAllowed(
                    exceptionType == null
                            ? definition.calendar().isWorkday(date)
                            : exceptionType == CalendarDateType.WORKDAY);
            result.add(item);
        }
        return List.copyOf(result);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCalendarChanged(CalendarCacheInvalidationEvent event) {
        if (event != null && event.calendarId() != null) {
            evict(event.calendarId());
        }
    }

    public void evict(Long calendarId) {
        if (calendarId != null) {
            cache.invalidate(calendarId);
            cache.cleanUp();
        }
    }

    public void evictAll() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    private CalendarDefinition loadDefinition(Long calendarId) {
        Calendar calendar = calendarMapper.selectById(calendarId);
        if (calendar == null) {
            return null;
        }
        if (!WorkdayMask.isValid(calendar.getWorkdaysMask())) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.CALENDAR_WORKDAY_CONFIGURATION_IS_INVALID);
        }

        List<CalendarException> exceptions = exceptionMapper.selectByCalendarId(calendarId);
        Map<LocalDate, CalendarDateType> exceptionMap =
                exceptions == null
                        ? Map.of()
                        : exceptions.stream()
                                .collect(
                                        Collectors.toMap(
                                                CalendarException::getDate,
                                                CalendarException::getType,
                                                (first, second) -> second,
                                                LinkedHashMap::new));
        return new CalendarDefinition(calendar, exceptionMap);
    }

    private void deleteIds(List<Long> ids) {
        List<Long> existing = calendarMapper.selectIdsForUpdate(ids);
        if (existing == null) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, CoreMessage.CALENDAR_BATCH_LOCKING_FAILED);
        }
        if (!existing.equals(ids)) {
            throw new BusinessException(
                    ResultCode.NOT_FOUND, CoreMessage.SOME_CALENDARS_DO_NOT_EXIST);
        }
        if (scheduleMapper.countByCalendarIds(ids) > 0) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.CALENDAR_IS_STILL_REFERENCED_BY_SCHEDULES);
        }
        if (calendarMapper.deleteByIds(ids) != ids.size()) {
            throw new BusinessException(
                    ResultCode.CONFLICT,
                    CoreMessage.CALENDAR_DELETION_CONFLICTS_WITH_A_CONCURRENT_UPDATE);
        }
        ids.forEach(id -> eventPublisher.publishEvent(new CalendarCacheInvalidationEvent(id)));
        log.info("calendars deleted, count={}", ids.size());
    }

    public void validateImport(CalendarUpsertRequest request) {
        validateRequest(request);
    }

    private List<CalendarExceptionRequest> validateRequest(CalendarUpsertRequest request) {
        if (request == null) {
            throw paramError(CoreMessage.REQUEST_BODY_IS_REQUIRED);
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw paramError(CoreMessage.CALENDAR_NAME_IS_REQUIRED);
        }
        if (request.getName().trim().length() > MAX_NAME_LENGTH) {
            throw paramError(CoreMessage.CALENDAR_NAME_MUST_NOT_EXCEED_128_CHARACTERS);
        }
        if (request.getDescription() != null
                && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw paramError(CoreMessage.CALENDAR_DESCRIPTION_MUST_NOT_EXCEED_500_CHARACTERS);
        }
        Set<DayOfWeek> workdays = request.getWorkdays();
        if (workdays == null || workdays.isEmpty() || workdays.stream().anyMatch(Objects::isNull)) {
            throw paramError(CoreMessage.AT_LEAST_ONE_VALID_WORKDAY_IS_REQUIRED);
        }

        List<CalendarExceptionRequest> exceptions =
                request.getExceptions() == null
                        ? List.of()
                        : new ArrayList<>(request.getExceptions());
        if (exceptions.size() > MAX_EXCEPTION_COUNT) {
            throw paramError(CoreMessage.EXCEPTION_DATES_MUST_NOT_EXCEED_2000_ITEMS);
        }
        Set<LocalDate> dates = new HashSet<>();
        for (CalendarExceptionRequest exception : exceptions) {
            if (exception == null || exception.getDate() == null || exception.getType() == null) {
                throw paramError(CoreMessage.EXCEPTION_DATE_AND_TYPE_ARE_REQUIRED);
            }
            if (!dates.add(exception.getDate())) {
                throw paramError(CoreMessage.EXCEPTION_DATES_MUST_BE_UNIQUE);
            }
        }
        return exceptions.stream()
                .sorted(Comparator.comparing(CalendarExceptionRequest::getDate))
                .toList();
    }

    private Calendar toCalendar(CalendarUpsertRequest request) {
        Calendar calendar = new Calendar();
        calendar.setWorkdaysMask(WorkdayMask.fromDays(request.getWorkdays()));
        calendar.setDescription(trimToNull(request.getDescription()));
        return calendar;
    }

    private void insertExceptions(
            Long calendarId, List<CalendarExceptionRequest> requests, LocalDateTime now) {
        for (CalendarExceptionRequest request : requests) {
            CalendarException exception = new CalendarException();
            exception.setCalendarId(calendarId);
            exception.setDate(request.getDate());
            exception.setType(request.getType());
            exception.setCreateTime(now);
            exception.setUpdateTime(now);
            if (exceptionMapper.insert(exception) != 1) {
                throw new BusinessException(
                        ResultCode.SYSTEM_ERROR, CoreMessage.CALENDAR_EXCEPTION_PERSISTENCE_FAILED);
            }
        }
    }

    private Calendar requireCalendar(Long calendarId) {
        Long id = requirePositiveId(calendarId);
        Calendar calendar = calendarMapper.selectById(id);
        if (calendar == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.CALENDAR_DOES_NOT_EXIST);
        }
        return calendar;
    }

    private void ensureNameAvailable(String name, Long currentId) {
        Calendar duplicate = calendarMapper.selectByName(name);
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentId)) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.CALENDAR_NAME_ALREADY_EXISTS);
        }
    }

    private CalendarListDTO toListDto(Calendar calendar) {
        CalendarListDTO dto = new CalendarListDTO();
        dto.setId(calendar.getId());
        dto.setName(calendar.getName());
        dto.setWorkdays(WorkdayMask.toDays(calendar.getWorkdaysMask()));
        dto.setDescription(calendar.getDescription());
        dto.setUpdateTime(calendar.getUpdateTime());
        return dto;
    }

    private CalendarDetailDTO toDetailDto(Calendar calendar, List<CalendarException> exceptions) {
        CalendarDetailDTO dto = new CalendarDetailDTO();
        dto.setId(calendar.getId());
        dto.setName(calendar.getName());
        dto.setWorkdays(WorkdayMask.toDays(calendar.getWorkdaysMask()));
        dto.setDescription(calendar.getDescription());
        dto.setCreateTime(calendar.getCreateTime());
        dto.setUpdateTime(calendar.getUpdateTime());
        dto.setExceptions(
                exceptions == null
                        ? List.of()
                        : exceptions.stream()
                                .sorted(Comparator.comparing(CalendarException::getDate))
                                .map(this::toExceptionDto)
                                .toList());
        return dto;
    }

    private CalendarExceptionDTO toExceptionDto(CalendarException exception) {
        CalendarExceptionDTO dto = new CalendarExceptionDTO();
        dto.setId(exception.getId());
        dto.setDate(exception.getDate());
        dto.setType(exception.getType());
        return dto;
    }

    private Long requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw paramError(CoreMessage.CALENDAR_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        return id;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BusinessException paramError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
