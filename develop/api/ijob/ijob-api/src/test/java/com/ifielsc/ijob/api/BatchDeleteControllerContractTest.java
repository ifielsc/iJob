// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifielsc.ijob.api.calendar.CalendarController;
import com.ifielsc.ijob.api.event.EventBindingController;
import com.ifielsc.ijob.api.job.JobController;
import com.ifielsc.ijob.api.notify.NotifyBindingController;
import com.ifielsc.ijob.api.notify.NotifyConfigController;
import com.ifielsc.ijob.api.notify.NotifySendLogController;
import com.ifielsc.ijob.api.resource.ResourceController;
import com.ifielsc.ijob.api.schedule.ScheduleController;
import com.ifielsc.ijob.api.task.TaskController;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.util.List;

class BatchDeleteControllerContractTest {
    @Test
    void managementControllersShouldExposeUniformBatchDeleteContract() throws Exception {
        for (Class<?> controller :
                List.of(
                        CalendarController.class,
                        ResourceController.class,
                        TaskController.class,
                        JobController.class,
                        ScheduleController.class,
                        NotifyConfigController.class,
                        NotifyBindingController.class,
                        EventBindingController.class)) {
            Method method = controller.getMethod("deleteBatch", BatchDeleteRequest.class);
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            assertNotNull(mapping, controller.getSimpleName());
            assertArrayEquals(new String[] {"/batch"}, mapping.value());
            assertEquals(ApiResponse.class, method.getReturnType());
            assertNotNull(method.getParameters()[0].getAnnotation(RequestBody.class));
        }
    }

    @Test
    void notificationAuditLogShouldRemainReadOnly() {
        assertFalse(
                List.of(NotifySendLogController.class.getDeclaredMethods()).stream()
                        .anyMatch(method -> method.isAnnotationPresent(DeleteMapping.class)));
    }
}
