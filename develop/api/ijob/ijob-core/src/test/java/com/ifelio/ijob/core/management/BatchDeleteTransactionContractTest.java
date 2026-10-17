package com.ifelio.ijob.core.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.core.calendar.CalendarService;
import com.ifelio.ijob.core.event.EventBindingService;
import com.ifelio.ijob.core.job.JobService;
import com.ifelio.ijob.core.job.retirement.JobRetirementService;
import com.ifelio.ijob.core.notify.NotifyBindingService;
import com.ifelio.ijob.core.notify.NotifyConfigService;
import com.ifelio.ijob.core.resource.ResourceService;
import com.ifelio.ijob.core.schedule.ScheduleService;
import com.ifelio.ijob.core.task.TaskService;
import com.ifelio.ijob.core.task.retirement.TaskRetirementService;

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
