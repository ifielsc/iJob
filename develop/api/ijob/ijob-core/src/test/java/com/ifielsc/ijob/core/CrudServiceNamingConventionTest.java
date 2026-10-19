package com.ifielsc.ijob.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.calendar.CalendarService;
import com.ifielsc.ijob.core.job.JobService;
import com.ifielsc.ijob.core.notify.NotifyBindingService;
import com.ifielsc.ijob.core.notify.NotifyConfigService;
import com.ifielsc.ijob.core.resource.ResourceService;
import com.ifielsc.ijob.core.schedule.ScheduleService;
import com.ifielsc.ijob.core.task.TaskService;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

class CrudServiceNamingConventionTest {
    private static final Set<String> LEGACY_NAMES =
            Set.of(
                    "queryResourcePage",
                    "getResourceDetail",
                    "createResource",
                    "updateResource",
                    "deleteResource",
                    "deleteResources",
                    "queryCalendarPage",
                    "getCalendarDetail",
                    "createCalendar",
                    "updateCalendar",
                    "deleteCalendar",
                    "queryNotifyConfigPage",
                    "getNotifyConfigDetail",
                    "createNotifyConfig",
                    "updateNotifyConfig",
                    "deleteNotifyConfig",
                    "listBindingDetails",
                    "getBindingDetail",
                    "createBinding",
                    "updateBinding",
                    "deleteBinding",
                    "queryTaskPage",
                    "getTaskDetail",
                    "createTask",
                    "updateTask",
                    "deleteTask",
                    "deleteTasks",
                    "queryJobPage",
                    "getJobDetail",
                    "createJobDetail",
                    "updateJobDetail",
                    "deleteJob",
                    "deleteJobs");

    @Test
    void activeCrudFacadesUseContextFreeCrudNames() {
        assertCrudMethods(
                ResourceService.class,
                "queryPage",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
        assertCrudMethods(
                CalendarService.class,
                "queryPage",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
        assertCrudMethods(
                NotifyConfigService.class,
                "queryPage",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
        assertCrudMethods(
                NotifyBindingService.class,
                "listDetailsByTarget",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
        assertCrudMethods(
                TaskService.class,
                "queryPage",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
        assertCrudMethods(
                JobService.class,
                "queryPage",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
        assertCrudMethods(
                ScheduleService.class,
                "queryPage",
                "getDetail",
                "create",
                "update",
                "delete",
                "deleteBatch");
    }

    private void assertCrudMethods(Class<?> serviceType, String... expectedNames) {
        Set<String> names =
                Arrays.stream(serviceType.getDeclaredMethods())
                        .map(Method::getName)
                        .collect(Collectors.toSet());
        for (String expectedName : expectedNames) {
            assertTrue(
                    names.contains(expectedName),
                    () -> serviceType.getSimpleName() + " is missing " + expectedName);
        }
        assertFalse(
                names.stream().anyMatch(LEGACY_NAMES::contains),
                () -> serviceType.getSimpleName() + " still exposes a legacy CRUD name");
    }
}
