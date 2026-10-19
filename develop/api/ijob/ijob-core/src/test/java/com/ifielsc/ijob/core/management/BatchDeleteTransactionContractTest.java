// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.core.calendar.CalendarService;
import com.ifielsc.ijob.core.event.EventBindingService;
import com.ifielsc.ijob.core.job.JobService;
import com.ifielsc.ijob.core.job.retirement.JobRetirementService;
import com.ifielsc.ijob.core.notify.NotifyBindingService;
import com.ifielsc.ijob.core.notify.NotifyConfigService;
import com.ifielsc.ijob.core.resource.ResourceService;
import com.ifielsc.ijob.core.schedule.ScheduleService;
import com.ifielsc.ijob.core.task.TaskService;
import com.ifielsc.ijob.core.task.retirement.TaskRetirementService;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

class BatchDeleteTransactionContractTest {
    @Test
    void everyBatchDeleteEntrypointShouldUseOneRequiredWriteTransaction() throws Exception {
        for (Class<?> service :
                List.of(
                        CalendarService.class,
                        ResourceService.class,
                        TaskService.class,
                        JobService.class,
                        ScheduleService.class,
                        NotifyConfigService.class,
                        NotifyBindingService.class,
                        EventBindingService.class)) {
            assertWriteTransaction(service.getMethod("deleteBatch", BatchDeleteRequest.class));
        }
        assertWriteTransaction(TaskRetirementService.class.getMethod("retireBatch", List.class));
        assertWriteTransaction(JobRetirementService.class.getMethod("retireBatch", List.class));
    }

    private void assertWriteTransaction(Method method) {
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull(annotation, method::toString);
        assertFalse(annotation.readOnly(), method::toString);
        assertEquals(Propagation.REQUIRED, annotation.propagation(), method::toString);
    }
}
