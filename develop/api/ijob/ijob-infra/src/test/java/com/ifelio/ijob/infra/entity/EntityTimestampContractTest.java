package com.ifelio.ijob.infra.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.infra.entity.calendar.Calendar;
import com.ifelio.ijob.infra.entity.calendar.CalendarException;
import com.ifelio.ijob.infra.entity.event.EventBinding;
import com.ifelio.ijob.infra.entity.job.Job;
import com.ifelio.ijob.infra.entity.notify.NotifyBinding;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;
import com.ifelio.ijob.infra.entity.notify.NotifySendLog;
import com.ifelio.ijob.infra.entity.resource.Resource;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.entity.schedule.Schedule;
import com.ifelio.ijob.infra.entity.task.Task;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

class EntityTimestampContractTest {
    private static final List<Class<?>> TIMESTAMPED_ENTITIES =
            List.of(
                    Calendar.class,
                    CalendarException.class,
                    EventBinding.class,
                    Job.class,
                    NotifyBinding.class,
                    NotifyConfig.class,
                    NotifySendLog.class,
                    Resource.class,
                    Instance.class,
                    Trigger.class,
                    Schedule.class,
                    Task.class);

    @Test
    void timestampedEntitiesShouldOwnTheirMappedFieldsAndAccessors() throws Exception {
        LocalDateTime createTime = LocalDateTime.of(2026, 9, 12, 10, 0);
        LocalDateTime updateTime = createTime.plusMinutes(1);

        for (Class<?> type : TIMESTAMPED_ENTITIES) {
            assertEquals(Object.class, type.getSuperclass(), type.getName());
            assertTimestampField(type, "createTime");
            assertTimestampField(type, "updateTime");

            Object entity = type.getConstructor().newInstance();
            type.getMethod("setCreateTime", LocalDateTime.class).invoke(entity, createTime);
            type.getMethod("setUpdateTime", LocalDateTime.class).invoke(entity, updateTime);
            assertEquals(
                    createTime, type.getMethod("getCreateTime").invoke(entity), type.getName());
            assertEquals(
                    updateTime, type.getMethod("getUpdateTime").invoke(entity), type.getName());
        }
    }

    @Test
    void obsoleteBaseEntityShouldNotRemainOnClasspath() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("com.ifelio.ijob.infra.entity.common.BaseEntity"));
    }

    private void assertTimestampField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        assertEquals(LocalDateTime.class, field.getType(), type.getName() + "." + name);
    }
}
