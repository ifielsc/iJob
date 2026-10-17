package com.ifelio.ijob.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.notify.MailConnectionDTO;
import com.ifelio.ijob.core.auth.AuditService;
import com.ifelio.ijob.core.auth.AuthService;
import com.ifelio.ijob.infra.entity.auth.OperationAudit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;

class OperationAuditConfigurationTest {
    private static final String ID = "9007199254740993";
    private final RecordingManager manager = new RecordingManager();
    private final RecordingAudit audit = new RecordingAudit(manager);

    @Test
    void explicitCreateActionsKeepExactStringTargetIdsThroughTheProxy() throws Exception {
        var commands = proxy();
        for (int i = 0; i < 9; i++) {
            Commands.class.getMethod("create" + i).invoke(commands);
            var event = audit.events.getLast();
            assertEquals(ID, event.getTargetId());
            assertEquals("SUCCESS", event.getResultCode());
        }
        assertEquals(9, manager.commits);
    }

    @Test
    void bindTargetIncludesItsKindAndIdInsteadOfBeingMistakenForTemplateId() {
        proxy().bind("TASK", Long.valueOf(ID));
        assertEquals("PARAMETER_BINDING", audit.events.getLast().getTargetType());
        assertEquals("TASK:" + ID, audit.events.getLast().getTargetId());
    }

    @Test
    void mailFailureIsPairedWithAttemptAndMarkedFailed() {
        proxy().mail(false);
        assertEquals(
                List.of("ATTEMPTED", "FAILED"),
                audit.events.stream().map(OperationAudit::getResultCode).toList());
        assertEquals(
                audit.events.getFirst().getId().toString(), audit.events.getLast().getRequestId());
    }

    @Test
    void successfulMailAndAcceptedCorrelationKeepTheirExistingMeaning() {
        var commands = proxy();
        commands.mail(true);
        assertEquals("SUCCESS", audit.events.getLast().getResultCode());
        commands.trigger();
        assertNull(audit.events.getLast().getTargetId());
        assertEquals(ID, audit.events.getLast().getRequestId());
        assertEquals("ACCEPTED", audit.events.getLast().getResultCode());
        commands.arbitraryString();
        assertNull(audit.events.getLast().getTargetId());
    }

    @Test
    void failedCommandRollsBackAndDoesNotInventCreatedTarget() {
        assertThrows(IllegalStateException.class, () -> proxy().failedCreate());
        assertEquals(1, manager.rollbacks);
        assertEquals("FAILED", audit.events.getLast().getResultCode());
        assertNull(audit.events.getLast().getTargetId());
    }

    private Commands proxy() {
        var properties = new AppProperties();
        properties.getAuth().setMode(AppProperties.AuthMode.STATIC);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("audit", audit);
        beans.registerSingleton("manager", manager);
        beans.registerSingleton("properties", properties);
        beans.registerSingleton(
                "auth",
                new AuthService(properties, null, null) {
                    @Override
                    public CurrentUserResponse currentUser() {
                        return null;
                    }
                });
        var processor =
                OperationAuditConfiguration.operationAuditCommands(
                        beans.getBeanProvider(AuditService.class),
                                beans.getBeanProvider(AuthService.class),
                        beans.getBeanProvider(AppProperties.class),
                                beans.getBeanProvider(PlatformTransactionManager.class));
        return (Commands) processor.postProcessAfterInitialization(new Commands(), "commands");
    }

    public static class Commands {
        @AuditOperation(action = "CALENDAR_CREATE", target = "RESOURCE")
        public ApiResponse<String> create0() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "EVENT_BINDING_CREATE", target = "RESOURCE")
        public ApiResponse<String> create1() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "NOTIFY_BINDING_CREATE", target = "RESOURCE")
        public ApiResponse<String> create2() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "NOTIFY_CONFIG_CREATE", target = "RESOURCE")
        public ApiResponse<String> create3() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "MAIL_CHANNEL_CREATE", target = "RESOURCE")
        public ApiResponse<String> create4() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "PARAMETER_TEMPLATE_CREATE", target = "RESOURCE")
        public ApiResponse<String> create5() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "RESOURCE_CREATE", target = "RESOURCE")
        public ApiResponse<String> create6() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "SCHEDULE_CREATE", target = "RESOURCE")
        public ApiResponse<String> create7() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "CONCURRENCY_POLICY_CREATE_RESOURCE", target = "RESOURCE")
        public ApiResponse<String> create8() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "PARAMETER_TEMPLATE_BIND", target = "PARAMETER_BINDING")
        public ApiResponse<Void> bind(@PathVariable String type, @PathVariable Long targetId) {
            return ApiResponse.success();
        }

        @AuditOperation(
                action = "MAIL_CHANNEL_TEST_CONNECTION",
                target = "MAIL_CHANNEL",
                external = true)
        public ApiResponse<MailConnectionDTO> mail(boolean success) {
            return ApiResponse.success(
                    new MailConnectionDTO(success, success ? null : "CONNECTION_FAILED"));
        }

        @AuditOperation(action = "TASK_TRIGGER", target = "TASK", accepted = true)
        public ApiResponse<String> trigger() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "OTHER", target = "RESOURCE")
        public ApiResponse<String> arbitraryString() {
            return ApiResponse.success(ID);
        }

        @AuditOperation(action = "RESOURCE_CREATE", target = "RESOURCE")
        public ApiResponse<String> failedCreate() {
            throw new IllegalStateException("fixture failure");
        }
    }

    private static class RecordingManager implements PlatformTransactionManager {
        int commits;
        int rollbacks;

        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        public void commit(TransactionStatus status) {
            commits++;
        }

        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }

    private static class RecordingAudit extends AuditService {
        private long sequence;
        private final List<OperationAudit> events = new ArrayList<>();

        RecordingAudit(PlatformTransactionManager manager) {
            super(null, null, manager);
        }

        @Override
        public OperationAudit prepare(
                CurrentUserResponse actor,
                String action,
                String type,
                String target,
                String fields) {
            var event = new OperationAudit();
            event.setId(++sequence);
            event.setActionCode(action);
            event.setTargetType(type);
            event.setTargetId(target);
            return event;
        }

        @Override
        public void append(OperationAudit event, String result, String request) {
            event.setResultCode(result);
            event.setRequestId(request);
            events.add(event);
        }

        @Override
        public void independent(OperationAudit event, String result, String request) {
            append(event, result, request);
        }

        @Override
        public void bestEffort(OperationAudit event, String result) {
            append(event, result, null);
        }
    }
}
