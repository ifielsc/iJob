package com.ifelio.ijob.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifelio.ijob.contract.enums.ScheduleType;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.schedule.ScheduleDTO;
import com.ifelio.ijob.contract.schedule.ScheduleEnabledRequest;
import com.ifelio.ijob.contract.schedule.SchedulePageQuery;
import com.ifelio.ijob.contract.schedule.SchedulePreviewDTO;
import com.ifelio.ijob.contract.schedule.SchedulePreviewRequest;
import com.ifelio.ijob.contract.schedule.ScheduleUpsertRequest;
import com.ifelio.ijob.core.execute.variable.VariableSnapshotService;
import com.ifelio.ijob.core.job.definition.JobAdmissionResolver;
import com.ifelio.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifelio.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifelio.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifelio.ijob.infra.entity.schedule.Schedule;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifelio.ijob.infra.mapper.job.JobPublicationMapper;
import com.ifelio.ijob.infra.mapper.schedule.ScheduleMapper;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifelio.ijob.infra.mapper.task.TaskPublicationMapper;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ScheduleServiceTest {
    @Test
    void createShouldPersistUtcAndExposeBusinessZoneTime() {
        RecordingScheduleMapper mapper = new RecordingScheduleMapper();
        ScheduleService service =
                new ScheduleService(
                        mapper,
                        null,
                        new RecordingTaskResolver(),
                        new RecordingJobResolver(),
                        new VariableSnapshotService(new ObjectMapper()),
                        Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC),
                        ZoneId.of("Asia/Shanghai"));
        ScheduleUpsertRequest request = new ScheduleUpsertRequest();
        request.setTargetType(TargetType.TASK);
        request.setTargetId(7L);
        request.setScheduleType(ScheduleType.ONCE);
        request.setStartTime(LocalDateTime.of(2026, 9, 12, 9, 0));
        request.setEnabled(true);

        Long id = service.create(request);
        ScheduleDTO detail = service.getDetail(id);

        assertEquals(LocalDateTime.of(2026, 9, 12, 1, 0), mapper.stored.getStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 12, 1, 0), mapper.stored.getNextFireTime());
        assertEquals(LocalDateTime.of(2026, 9, 12, 0, 0), mapper.stored.getCreateTime());
        assertEquals(LocalDateTime.of(2026, 9, 12, 9, 0), detail.getStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 12, 9, 0), detail.getNextFireTime());
    }

    @Test
    void createShouldValidateTaskAndInitializeIntervalFireTime() {
        RecordingScheduleMapper mapper = new RecordingScheduleMapper();
        RecordingTaskResolver tasks = new RecordingTaskResolver();
        ScheduleService service = service(mapper, tasks, new RecordingJobResolver());
        ScheduleUpsertRequest request = intervalRequest(TargetType.TASK, 7L);
        request.setVariables(Map.of("batch", 7));

        Long id = service.create(request);

        assertEquals(101L, id);
        assertEquals(7L, tasks.id);
        assertEquals(TargetType.TASK, mapper.stored.getTargetType());
        assertEquals(60, mapper.stored.getIntervalSec());
        assertNotNull(mapper.stored.getNextFireTime());
        assertNotNull(mapper.stored.getCreateTime());
        assertEquals("{\"batch\":7}", mapper.stored.getVariables());
    }

    @Test
    void updateAndEnableShouldPreserveLastFireTimeAndRecalculate() {
        RecordingScheduleMapper mapper = new RecordingScheduleMapper();
        mapper.stored = storedSchedule(9L);
        LocalDateTime previousFire = LocalDateTime.of(2026, 8, 15, 9, 0);
        mapper.stored.setLastFireTime(previousFire);
        ScheduleService service =
                service(mapper, new RecordingTaskResolver(), new RecordingJobResolver());
        ScheduleUpsertRequest request = intervalRequest(TargetType.JOB, 8L);

        service.update(9L, request);
        ScheduleEnabledRequest disabled = new ScheduleEnabledRequest();
        disabled.setEnabled(false);
        service.updateEnabled(9L, disabled);

        assertEquals(previousFire, mapper.stored.getLastFireTime());
        assertEquals(TargetType.JOB, mapper.stored.getTargetType());
        assertFalse(mapper.stored.getEnabled());
        assertNull(mapper.stored.getNextFireTime());
    }

    @Test
    void previewShouldBeDeterministicAndExposeCalendarDecision() {
        ScheduleService service =
                service(
                        new RecordingScheduleMapper(),
                        new RecordingTaskResolver(),
                        new RecordingJobResolver());
        SchedulePreviewRequest request = new SchedulePreviewRequest();
        request.setScheduleType(ScheduleType.INTERVAL);
        request.setIntervalSec(30);
        request.setFromTime(LocalDateTime.of(2026, 8, 15, 10, 0));
        request.setCount(3);

        SchedulePreviewDTO result = service.preview(request);

        assertEquals(3, result.getItems().size());
        assertEquals(
                LocalDateTime.of(2026, 8, 15, 10, 0, 30),
                result.getItems().getFirst().getFireTime());
        assertEquals(
                LocalDateTime.of(2026, 8, 15, 10, 1, 30),
                result.getItems().getLast().getFireTime());
        assertTrue(result.getItems().stream().allMatch(item -> item.getCalendarAllowed()));
    }

    @Test
    void previewShouldSupportOnceAndRejectInvalidCron() {
        ScheduleService service =
                service(
                        new RecordingScheduleMapper(),
                        new RecordingTaskResolver(),
                        new RecordingJobResolver());
        SchedulePreviewRequest once = new SchedulePreviewRequest();
        once.setScheduleType(ScheduleType.ONCE);
        once.setStartTime(LocalDateTime.of(2026, 8, 16, 12, 0));
        once.setFromTime(LocalDateTime.of(2026, 8, 15, 12, 0));
        once.setCount(10);

        SchedulePreviewDTO result = service.preview(once);

        assertEquals(1, result.getItems().size());
        assertEquals(once.getStartTime(), result.getItems().getFirst().getFireTime());

        SchedulePreviewRequest invalid = new SchedulePreviewRequest();
        invalid.setScheduleType(ScheduleType.CRON);
        invalid.setCronExpr("invalid");
        BusinessException error =
                assertThrows(BusinessException.class, () -> service.preview(invalid));
        assertEquals(ResultCode.PARAM_ERROR, error.getCode());
    }

    @Test
    void pageDetailAndDeleteShouldUseContractBoundary() {
        RecordingScheduleMapper mapper = new RecordingScheduleMapper();
        mapper.stored = storedSchedule(12L);
        ScheduleService service =
                service(mapper, new RecordingTaskResolver(), new RecordingJobResolver());
        SchedulePageQuery query = new SchedulePageQuery();

        ScheduleDTO detail = service.getDetail(12L);
        var page = service.queryPage(query);
        service.delete(12L);

        assertEquals(12L, detail.getId());
        assertEquals(Map.of(), detail.getVariables());
        assertSame(query, mapper.query);
        assertEquals(1, page.getList().size());
        assertTrue(mapper.deleted);
    }

    @Test
    void catchUpMisfireShouldRespectPerScheduleAndScanBudgets() {
        Schedule schedule = misfiredIntervalSchedule(ScheduleMisfirePolicy.CATCH_UP_BOUNDED);
        schedule.setMaxCatchupCount(3);

        ScheduleFirePlan plan = misfireService().planClaimedFire(schedule, 2);

        assertTrue(plan.misfire());
        assertEquals(
                List.of(LocalDateTime.of(2026, 9, 12, 0, 0), LocalDateTime.of(2026, 9, 12, 0, 1)),
                plan.fireTimes());
        assertEquals(LocalDateTime.of(2026, 9, 12, 0, 11), plan.nextFireTime());
        assertEquals(11, plan.dueCount());
        assertEquals(9, plan.skippedCount());
    }

    @Test
    void fireOnceMisfireShouldUseTheLatestDueTime() {
        Schedule schedule = misfiredIntervalSchedule(ScheduleMisfirePolicy.FIRE_ONCE);

        ScheduleFirePlan plan = misfireService().planClaimedFire(schedule, 100);

        assertEquals(List.of(LocalDateTime.of(2026, 9, 12, 0, 10)), plan.fireTimes());
        assertEquals(LocalDateTime.of(2026, 9, 12, 0, 11), plan.nextFireTime());
        assertEquals(10, plan.skippedCount());
    }

    @Test
    void skipMisfireShouldAdvanceWithoutCreatingTriggers() {
        Schedule schedule = misfiredIntervalSchedule(ScheduleMisfirePolicy.SKIP);

        ScheduleFirePlan plan = misfireService().planClaimedFire(schedule, 100);

        assertTrue(plan.fireTimes().isEmpty());
        assertEquals(LocalDateTime.of(2026, 9, 12, 0, 11), plan.nextFireTime());
        assertEquals(11, plan.skippedCount());
    }

    @Test
    void intervalCatchupMustNotCrossEndTimeWhenFastForwarding() {
        for (ScheduleMisfirePolicy policy : ScheduleMisfirePolicy.values()) {
            Schedule schedule = misfiredIntervalSchedule(policy);
            schedule.setNextFireTime(LocalDateTime.of(2026, 8, 1, 0, 0));
            schedule.setEndTime(LocalDateTime.of(2026, 8, 2, 0, 0));
            ScheduleFirePlan plan = misfireService().planClaimedFire(schedule, 100);
            assertTrue(plan.fireTimes().isEmpty(), policy.name());
            assertNull(plan.nextFireTime(), policy.name());
        }
    }

    private ScheduleService service(
            RecordingScheduleMapper mapper,
            RecordingTaskResolver tasks,
            RecordingJobResolver jobs) {
        return new ScheduleService(
                mapper, null, tasks, jobs, new VariableSnapshotService(new ObjectMapper()));
    }

    private ScheduleService misfireService() {
        AppProperties.Schedule properties = new AppProperties().getSchedule();
        return new ScheduleService(
                new RecordingScheduleMapper(),
                null,
                new RecordingTaskResolver(),
                new RecordingJobResolver(),
                new VariableSnapshotService(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-09-12T00:10:00Z"), ZoneOffset.UTC),
                ZoneOffset.UTC,
                properties,
                null);
    }

    private Schedule misfiredIntervalSchedule(ScheduleMisfirePolicy policy) {
        Schedule schedule = new Schedule();
        schedule.setId(9L);
        schedule.setScheduleType(ScheduleType.INTERVAL);
        schedule.setIntervalSec(60);
        schedule.setEnabled(true);
        schedule.setMisfirePolicy(policy);
        schedule.setMisfireGraceSec(60);
        schedule.setNextFireTime(LocalDateTime.of(2026, 9, 12, 0, 0));
        return schedule;
    }

    private ScheduleUpsertRequest intervalRequest(TargetType targetType, Long targetId) {
        ScheduleUpsertRequest request = new ScheduleUpsertRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setScheduleType(ScheduleType.INTERVAL);
        request.setIntervalSec(60);
        request.setEnabled(true);
        return request;
    }

    private Schedule storedSchedule(Long id) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setTargetType(TargetType.TASK);
        schedule.setTargetId(7L);
        schedule.setScheduleType(ScheduleType.INTERVAL);
        schedule.setIntervalSec(60);
        schedule.setEnabled(true);
        schedule.setNextFireTime(LocalDateTime.now().plusMinutes(1));
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        return schedule;
    }

    private static final class RecordingTaskResolver extends TaskAdmissionResolver {
        private Long id;

        private RecordingTaskResolver() {
            super((TaskPublicationMapper) null, (TaskDefinitionMapper) null);
        }

        @Override
        public TaskAdmissionSnapshot resolve(Long taskId) {
            id = taskId;
            return null;
        }
    }

    private static final class RecordingJobResolver extends JobAdmissionResolver {
        private Long id;

        private RecordingJobResolver() {
            super((JobPublicationMapper) null, (JobDefinitionMapper) null);
        }

        @Override
        public JobAdmissionSnapshot resolve(Long jobId) {
            id = jobId;
            return null;
        }
    }

    private static final class RecordingScheduleMapper implements ScheduleMapper {
        private Schedule stored;
        private SchedulePageQuery query;
        private boolean deleted;
        private LocalDateTime expectedNextFireTime;

        @Override
        public List<Schedule> selectList(SchedulePageQuery selected) {
            query = selected;
            if (stored == null) {
                return List.of();
            }
            return List.of(stored);
        }

        @Override
        public Schedule selectById(Long id) {
            return stored != null && id.equals(stored.getId()) ? stored : null;
        }

        @Override
        public Schedule selectByFireClaimToken(String fireClaimToken) {
            return stored;
        }

        @Override
        public List<Schedule> selectListByTargetId(TargetType type, Long targetId) {
            return stored == null ? List.of() : List.of(stored);
        }

        @Override
        public List<Long> selectIdsForTrigger(int limit) {
            return new ArrayList<>();
        }

        @Override
        public Long selectDueForTriggerForUpdate(Long id) {
            return stored != null && id.equals(stored.getId()) ? id : null;
        }

        @Override
        public int claimDueForTrigger(Long id, String fireClaimToken) {
            return stored != null && id.equals(stored.getId()) ? 1 : 0;
        }

        @Override
        public int insert(Schedule schedule) {
            schedule.setId(101L);
            stored = schedule;
            return 1;
        }

        @Override
        public int updateById(Schedule schedule) {
            stored = schedule;
            return 1;
        }

        @Override
        public int updateEnabledById(Schedule schedule) {
            stored = schedule;
            return 1;
        }

        @Override
        public int updateFireTimeIfClaimed(
                Schedule schedule, LocalDateTime expectedNext, String fireClaimToken) {
            expectedNextFireTime = expectedNext;
            stored = schedule;
            return 1;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return stored != null && ids.contains(stored.getId())
                    ? List.of(stored.getId())
                    : List.of();
        }

        @Override
        public int disableByTargetId(TargetType type, Long targetId) {
            return 1;
        }

        @Override
        public int countByCalendarIds(List<Long> calendarIds) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            deleted = true;
            return 1;
        }

        @Override
        public int deleteByIds(List<Long> ids) {
            deleted = true;
            return ids.size();
        }
    }
}
