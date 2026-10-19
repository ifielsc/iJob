// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.calendar.CalendarDetailDTO;
import com.ifielsc.ijob.contract.calendar.CalendarExceptionRequest;
import com.ifielsc.ijob.contract.calendar.CalendarListDTO;
import com.ifielsc.ijob.contract.calendar.CalendarPageQuery;
import com.ifielsc.ijob.contract.calendar.CalendarPreviewDayDTO;
import com.ifielsc.ijob.contract.calendar.CalendarUpsertRequest;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.CalendarDateType;
import com.ifielsc.ijob.infra.entity.calendar.Calendar;
import com.ifielsc.ijob.infra.entity.calendar.CalendarException;
import com.ifielsc.ijob.infra.entity.calendar.WorkdayMask;
import com.ifielsc.ijob.infra.mapper.calendar.CalendarExceptionMapper;
import com.ifielsc.ijob.infra.mapper.calendar.CalendarMapper;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class CalendarServiceTest {
    @Test
    void createShouldNormalizeAggregateAndSortExceptions() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        CalendarService service = service(calendars, exceptions, new ArrayList<>());
        CalendarUpsertRequest request = validRequest();
        request.setName("  business days  ");
        request.setExceptions(
                List.of(
                        exception(LocalDate.of(2026, 10, 2), CalendarDateType.HOLIDAY),
                        exception(LocalDate.of(2026, 10, 1), CalendarDateType.WORKDAY)));

        Long id = service.create(request);

        assertEquals(101L, id);
        assertEquals("business days", calendars.calendar.getName());
        assertEquals(
                WorkdayMask.MONDAY
                        | WorkdayMask.TUESDAY
                        | WorkdayMask.WEDNESDAY
                        | WorkdayMask.THURSDAY
                        | WorkdayMask.FRIDAY,
                calendars.calendar.getWorkdaysMask());
        assertNotNull(calendars.calendar.getCreateTime());
        assertEquals(2, exceptions.items.size());
        assertEquals(LocalDate.of(2026, 10, 1), exceptions.items.get(0).getDate());
        assertEquals(LocalDate.of(2026, 10, 2), exceptions.items.get(1).getDate());
    }

    @Test
    void createShouldRejectEmptyWorkdaysAndDuplicateDates() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        CalendarService service =
                service(calendars, new RecordingExceptionMapper(), new ArrayList<>());
        CalendarUpsertRequest empty = validRequest();
        empty.setWorkdays(Set.of());

        BusinessException emptyError =
                assertThrows(BusinessException.class, () -> service.create(empty));
        assertEquals(ResultCode.PARAM_ERROR, emptyError.getCode());

        CalendarUpsertRequest duplicate = validRequest();
        LocalDate date = LocalDate.of(2026, 1, 1);
        duplicate.setExceptions(
                List.of(
                        exception(date, CalendarDateType.WORKDAY),
                        exception(date, CalendarDateType.HOLIDAY)));
        BusinessException duplicateError =
                assertThrows(BusinessException.class, () -> service.create(duplicate));
        assertEquals("Exception dates must be unique", duplicateError.getMessage());
        assertEquals(null, calendars.calendar);
    }

    @Test
    void listAndDetailShouldReturnContractDaysAndSortedExceptions() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(7L);
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        exceptions.items.add(
                storedException(12L, 7L, LocalDate.of(2026, 5, 2), CalendarDateType.HOLIDAY));
        exceptions.items.add(
                storedException(11L, 7L, LocalDate.of(2026, 5, 1), CalendarDateType.WORKDAY));
        CalendarService service = service(calendars, exceptions, new ArrayList<>());

        PageResult<CalendarListDTO> page = service.queryPage(new CalendarPageQuery());
        CalendarDetailDTO detail = service.getDetail(7L);

        assertEquals(1, page.getList().size());
        assertEquals(
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY),
                page.getList().getFirst().getWorkdays());
        assertEquals(2, detail.getExceptions().size());
        assertEquals(LocalDate.of(2026, 5, 1), detail.getExceptions().getFirst().getDate());
    }

    @Test
    void updateShouldReplaceExceptionsAndPublishInvalidation() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(8L);
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        exceptions.items.add(
                storedException(1L, 8L, LocalDate.of(2026, 1, 1), CalendarDateType.HOLIDAY));
        List<Object> events = new ArrayList<>();
        CalendarService service = service(calendars, exceptions, events);
        CalendarUpsertRequest request = validRequest();
        request.setWorkdays(Set.of(DayOfWeek.SATURDAY));
        request.setExceptions(
                List.of(exception(LocalDate.of(2026, 2, 1), CalendarDateType.WORKDAY)));

        service.update(8L, request);

        assertEquals(WorkdayMask.SATURDAY, calendars.calendar.getWorkdaysMask());
        assertTrue(exceptions.deleteCalled);
        assertEquals(1, exceptions.items.size());
        assertEquals(LocalDate.of(2026, 2, 1), exceptions.items.getFirst().getDate());
        CalendarCacheInvalidationEvent event =
                assertInstanceOf(CalendarCacheInvalidationEvent.class, events.getFirst());
        assertEquals(8L, event.calendarId());
    }

    @Test
    void runtimeShouldApplyExceptionsCacheAndInvalidation() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(9L);
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        LocalDate monday = LocalDate.of(2026, 8, 10);
        LocalDate sunday = LocalDate.of(2026, 8, 16);
        exceptions.items.add(storedException(1L, 9L, monday, CalendarDateType.HOLIDAY));
        exceptions.items.add(storedException(2L, 9L, sunday, CalendarDateType.WORKDAY));
        CalendarService service = service(calendars, exceptions, new ArrayList<>());

        assertFalse(service.allowExecute(9L, monday));
        assertTrue(service.allowExecute(9L, sunday));
        assertTrue(service.allowExecute(9L, monday.plusDays(1)));
        assertEquals(1, calendars.selectByIdCount);

        exceptions.items.clear();
        service.onCalendarChanged(new CalendarCacheInvalidationEvent(9L));
        assertTrue(service.allowExecute(9L, monday));
        assertEquals(2, calendars.selectByIdCount);
    }

    @Test
    void runtimeCacheShouldReloadWithinConfiguredCrossInstanceStalenessWindow() throws Exception {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(9L);
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        LocalDate monday = LocalDate.of(2026, 8, 10);
        exceptions.items.add(storedException(1L, 9L, monday, CalendarDateType.HOLIDAY));
        AppProperties properties = new AppProperties();
        properties.getCacheConsistency().setMaxStaleness(Duration.ofMillis(1));
        CalendarService service =
                new CalendarService(
                        calendars, exceptions, scheduleMapper(0), event -> {}, properties);

        assertFalse(service.allowExecute(9L, monday));
        exceptions.items.clear();
        Thread.sleep(25L);

        assertTrue(service.allowExecute(9L, monday));
        assertEquals(2, calendars.selectByIdCount);
    }

    @Test
    void previewShouldReturnConsecutiveCalendarDecisions() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(19L);
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        LocalDate monday = LocalDate.of(2026, 8, 10);
        exceptions.items.add(storedException(1L, 19L, monday, CalendarDateType.HOLIDAY));
        CalendarService service = service(calendars, exceptions, new ArrayList<>());

        List<CalendarPreviewDayDTO> preview = service.preview(19L, monday, 2);

        assertEquals(2, preview.size());
        assertFalse(preview.getFirst().getAllowed());
        assertEquals(CalendarDateType.HOLIDAY, preview.getFirst().getExceptionType());
        assertTrue(preview.getLast().getAllowed());
    }

    @Test
    void previewDefaultShouldUseConfiguredBusinessDateInsteadOfUtcDate() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(20L);
        AppProperties properties = new AppProperties();
        properties.getSchedule().setZoneId("Asia/Shanghai");
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T16:30:00Z"), ZoneOffset.UTC);
        CalendarService service =
                new CalendarService(
                        calendars,
                        new RecordingExceptionMapper(),
                        scheduleMapper(0),
                        event -> {},
                        properties,
                        clock);

        List<CalendarPreviewDayDTO> preview = service.preview(20L, null, 1);

        assertEquals(LocalDate.of(2026, 8, 10), preview.getFirst().getDate());
        assertEquals(preview.getFirst().getDate(), service.context().today());
        assertEquals("Asia/Shanghai", service.context().timezone());
    }

    @Test
    void deleteShouldPublishInvalidationAndKeepDatabaseCascadeBoundary() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(10L);
        List<Object> events = new ArrayList<>();
        RecordingExceptionMapper exceptions = new RecordingExceptionMapper();
        CalendarService service = service(calendars, exceptions, events);

        service.delete(10L);

        assertTrue(calendars.deleteCalled);
        assertFalse(exceptions.deleteCalled);
        assertInstanceOf(CalendarCacheInvalidationEvent.class, events.getFirst());
    }

    @Test
    void batchDeleteShouldRejectPartialMissingSetBeforeAnyDelete() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(10L);
        CalendarService service =
                service(calendars, new RecordingExceptionMapper(), new ArrayList<>());
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(10L, 11L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteBatch(request));

        assertEquals(ResultCode.NOT_FOUND, exception.getCode());
        assertFalse(calendars.deleteCalled);
    }

    @Test
    void batchDeleteShouldRejectReferencedCalendarBeforeAnyDelete() {
        RecordingCalendarMapper calendars = new RecordingCalendarMapper();
        calendars.calendar = storedCalendar(10L);
        CalendarService service =
                new CalendarService(
                        calendars, new RecordingExceptionMapper(), scheduleMapper(1), event -> {});
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(10L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteBatch(request));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertFalse(calendars.deleteCalled);
    }

    private CalendarService service(
            RecordingCalendarMapper calendars,
            RecordingExceptionMapper exceptions,
            List<Object> events) {
        return new CalendarService(calendars, exceptions, scheduleMapper(0), events::add);
    }

    private ScheduleMapper scheduleMapper(int referenceCount) {
        return (ScheduleMapper)
                Proxy.newProxyInstance(
                        ScheduleMapper.class.getClassLoader(),
                        new Class<?>[] {ScheduleMapper.class},
                        (proxy, method, arguments) ->
                                method.getName().equals("countByCalendarIds")
                                        ? referenceCount
                                        : null);
    }

    private CalendarUpsertRequest validRequest() {
        CalendarUpsertRequest request = new CalendarUpsertRequest();
        request.setName("business-days");
        request.setWorkdays(
                new LinkedHashSet<>(
                        List.of(
                                DayOfWeek.MONDAY,
                                DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY,
                                DayOfWeek.FRIDAY)));
        request.setDescription("calendar");
        request.setExceptions(List.of());
        return request;
    }

    private CalendarExceptionRequest exception(LocalDate date, CalendarDateType type) {
        CalendarExceptionRequest request = new CalendarExceptionRequest();
        request.setDate(date);
        request.setType(type);
        return request;
    }

    private Calendar storedCalendar(Long id) {
        Calendar calendar = new Calendar();
        calendar.setId(id);
        calendar.setName("stored");
        calendar.setWorkdaysMask(
                WorkdayMask.MONDAY
                        | WorkdayMask.TUESDAY
                        | WorkdayMask.WEDNESDAY
                        | WorkdayMask.THURSDAY
                        | WorkdayMask.FRIDAY);
        calendar.setCreateTime(LocalDateTime.now());
        calendar.setUpdateTime(LocalDateTime.now());
        return calendar;
    }

    private CalendarException storedException(
            Long id, Long calendarId, LocalDate date, CalendarDateType type) {
        CalendarException exception = new CalendarException();
        exception.setId(id);
        exception.setCalendarId(calendarId);
        exception.setDate(date);
        exception.setType(type);
        return exception;
    }

    private static final class RecordingCalendarMapper implements CalendarMapper {
        private Calendar calendar;
        private Calendar duplicate;
        private boolean exists = true;
        private boolean deleteCalled;
        private int selectByIdCount;

        @Override
        public List<Calendar> selectList(CalendarPageQuery query) {
            return exists && calendar != null ? List.of(calendar) : List.of();
        }

        @Override
        public Calendar selectById(Long id) {
            selectByIdCount++;
            return exists && calendar != null && id.equals(calendar.getId()) ? calendar : null;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return exists && calendar != null && ids.contains(calendar.getId())
                    ? List.of(calendar.getId())
                    : List.of();
        }

        @Override
        public Calendar selectByName(String name) {
            return duplicate;
        }

        @Override
        public int insert(Calendar inserted) {
            inserted.setId(101L);
            calendar = inserted;
            exists = true;
            return 1;
        }

        @Override
        public int updateById(Calendar updated) {
            calendar = updated;
            return 1;
        }

        @Override
        public int deleteById(Long id) {
            deleteCalled = true;
            exists = false;
            return 1;
        }

        @Override
        public int deleteByIds(List<Long> ids) {
            deleteCalled = true;
            exists = false;
            return ids.size();
        }
    }

    private static final class RecordingExceptionMapper implements CalendarExceptionMapper {
        private final List<CalendarException> items = new ArrayList<>();
        private boolean deleteCalled;

        @Override
        public List<CalendarException> selectByCalendarId(Long calendarId) {
            return new ArrayList<>(items);
        }

        @Override
        public int insert(CalendarException exception) {
            exception.setId(200L + items.size());
            items.add(exception);
            return 1;
        }

        @Override
        public int deleteByCalendarId(Long calendarId) {
            deleteCalled = true;
            items.clear();
            return 1;
        }
    }
}
