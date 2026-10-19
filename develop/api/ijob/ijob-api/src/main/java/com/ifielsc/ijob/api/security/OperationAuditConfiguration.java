package com.ifielsc.ijob.api.security;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.event.EventReceiptDTO;
import com.ifielsc.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifielsc.ijob.core.auth.AuditService;
import com.ifielsc.ijob.core.auth.AuthService;
import com.ifielsc.ijob.infra.entity.auth.OperationAudit;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;

@Configuration
public class OperationAuditConfiguration {
    // These commands return a created resource ID, not a correlation or arbitrary text.
    private static final java.util.Set<String> STRING_ID_CREATES =
            java.util.Set.of(
                    "CALENDAR_CREATE",
                    "EVENT_BINDING_CREATE",
                    "NOTIFY_BINDING_CREATE",
                    "NOTIFY_CONFIG_CREATE",
                    "MAIL_CHANNEL_CREATE",
                    "PARAMETER_TEMPLATE_CREATE",
                    "RESOURCE_CREATE",
                    "SCHEDULE_CREATE",
                    "CONCURRENCY_POLICY_CREATE_RESOURCE");

    @Bean
    static BeanPostProcessor operationAuditCommands(
            ObjectProvider<AuditService> audits,
            ObjectProvider<AuthService> authentication,
            ObjectProvider<AppProperties> properties,
            ObjectProvider<PlatformTransactionManager> managers) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                Class<?> type = AopUtils.getTargetClass(bean);
                boolean annotated =
                        java.util.Arrays.stream(type.getMethods())
                                .anyMatch(m -> m.isAnnotationPresent(AuditOperation.class));
                if (!annotated) return bean;
                ProxyFactory proxy = new ProxyFactory(bean);
                proxy.setProxyTargetClass(true);
                MethodInterceptor interceptor =
                        invocation ->
                                execute(
                                        invocation,
                                        audits.getObject(),
                                        authentication.getObject(),
                                        properties.getObject(),
                                        managers.getObject());
                proxy.addAdvisor(
                        new DefaultPointcutAdvisor(
                                new AnnotationMatchingPointcut(null, AuditOperation.class),
                                interceptor));
                return proxy.getProxy();
            }
        };
    }

    private static Object execute(
            MethodInvocation invocation,
            AuditService audits,
            AuthService auth,
            AppProperties properties,
            PlatformTransactionManager manager)
            throws Throwable {
        if (properties.getAuth().getMode() == AppProperties.AuthMode.NONE)
            return invocation.proceed();
        Method method = invocation.getMethod();
        AuditOperation policy = method.getAnnotation(AuditOperation.class);
        CurrentUserResponse actor = auth.currentUser();
        String target = targetId(invocation, policy);
        OperationAudit event =
                audits.prepare(actor, policy.action(), policy.target(), target, policy.fields());
        java.util.List<OperationAudit> batchEvents = new java.util.ArrayList<>();
        for (Object argument : invocation.getArguments()) {
            if (argument instanceof com.ifielsc.ijob.contract.common.BatchDeleteRequest batch
                    && batch.getIds() != null) {
                if (batch.getIds().size() > 500)
                    throw new BusinessException(com.ifielsc.ijob.core.auth.SecurityError.INPUT);
                batch.getIds().stream()
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .forEach(
                                id ->
                                        batchEvents.add(
                                                audits.prepare(
                                                        actor,
                                                        policy.action(),
                                                        policy.target(),
                                                        id.toString(),
                                                        policy.fields())));
            }
        }
        try (var acceptance = com.ifielsc.ijob.core.auth.AuditOutcome.open()) {
            if (policy.external()) {
                audits.independent(event, "ATTEMPTED", null);
                Object result = invocation.proceed();
                OperationAudit completed =
                        audits.prepare(
                                actor, policy.action(), policy.target(), target, policy.fields());
                Object data = result instanceof ApiResponse<?> envelope ? envelope.getData() : null;
                boolean failed =
                        data
                                                instanceof
                                                com.ifielsc.ijob.contract.resource
                                                                        .ResourceConnectionTestDTO
                                                                test
                                        && !test.isSuccessful()
                                || data
                                                instanceof
                                                com.ifielsc.ijob.contract.notify.MailConnectionDTO
                                                                mail
                                        && !mail.connected();
                String outcome = failed ? "FAILED" : "SUCCESS";
                audits.independent(completed, outcome, event.getId().toString());
                return result;
            }
            TransactionTemplate tx = new TransactionTemplate(manager);
            return tx.execute(
                    status -> {
                        try {
                            Object result = invocation.proceed();
                            Object data =
                                    result instanceof ResponseEntity<?> response
                                            ? response.getBody()
                                            : result;
                            if (data instanceof ApiResponse<?> response) data = response.getData();
                            if (event.getTargetId() == null) {
                                if (data
                                        instanceof
                                        com.ifielsc.ijob.contract.auth.AccountDTO account)
                                    event.setTargetId(account.id());
                                else if (data
                                        instanceof
                                        com.ifielsc.ijob.contract.task.TaskPublicationDTO task)
                                    event.setTargetId(task.taskId().toString());
                                else if (data
                                        instanceof
                                        com.ifielsc.ijob.contract.job.JobPublicationDTO job)
                                    event.setTargetId(job.jobId().toString());
                                else if (STRING_ID_CREATES.contains(policy.action())
                                        && data instanceof String id
                                        && id.matches("[1-9][0-9]{0,18}")) event.setTargetId(id);
                                else if (data instanceof Long id && !policy.accepted())
                                    event.setTargetId(id.toString());
                            }
                            String outcome = policy.accepted() ? "ACCEPTED" : "SUCCESS";
                            String correlation = null;
                            if (data instanceof RuntimeCommandDTO command) {
                                correlation = command.getRequestId();
                                outcome = command.isReplayed() ? "REPLAYED" : "ACCEPTED";
                            } else if (data instanceof EventReceiptDTO receipt) {
                                correlation = receipt.eventId().toString();
                                outcome = receipt.duplicate() ? "REPLAYED" : "ACCEPTED";
                            } else if (policy.accepted()
                                    && (data instanceof Long
                                            || data instanceof String id
                                                    && id.matches("[1-9][0-9]{0,18}"))) {
                                correlation = data.toString();
                                if (acceptance.replayed()) outcome = "REPLAYED";
                            }
                            audits.append(event, outcome, correlation);
                            for (OperationAudit item : batchEvents)
                                audits.append(item, outcome, correlation);
                            return result;
                        } catch (RuntimeException | Error failure) {
                            throw failure;
                        } catch (Throwable failure) {
                            throw new CommandFailure(failure);
                        }
                    });
        } catch (Throwable failure) {
            try {
                OperationAudit rejected = event;
                if (policy.external())
                    rejected =
                            audits.prepare(
                                    actor,
                                    policy.action(),
                                    policy.target(),
                                    target,
                                    policy.fields());
                audits.bestEffort(
                        rejected, failure instanceof BusinessException ? "REJECTED" : "FAILED");
            } catch (RuntimeException ignored) {
                org.slf4j.LoggerFactory.getLogger(OperationAuditConfiguration.class)
                        .error("SECURITY_AUDIT_WRITE_FAILED; original command failure retained");
            }
            if (failure instanceof CommandFailure wrapper) throw wrapper.getCause();
            throw failure;
        }
    }

    private static String targetId(MethodInvocation invocation, AuditOperation policy) {
        if ("PARAMETER_TEMPLATE_BIND".equals(policy.action())) {
            Object[] arguments = invocation.getArguments();
            if (arguments.length >= 2
                    && arguments[0] instanceof String type
                    && ("TASK".equals(type) || "JOB".equals(type))
                    && arguments[1] instanceof Long id) {
                return type + ":" + id;
            }
            return null;
        }
        var parameters = invocation.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(PathVariable.class)) {
                Object value = invocation.getArguments()[i];
                if (value instanceof Number || value instanceof Enum<?>) return value.toString();
            }
        }
        return null;
    }

    private static class CommandFailure extends RuntimeException {
        CommandFailure(Throwable cause) {
            super(cause);
        }
    }
}
