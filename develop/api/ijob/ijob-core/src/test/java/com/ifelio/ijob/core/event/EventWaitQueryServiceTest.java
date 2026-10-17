package com.ifelio.ijob.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.EventWaitStatus;
import com.ifelio.ijob.contract.enums.EventWaitType;
import com.ifelio.ijob.infra.entity.runtime.EventWait;
import com.ifelio.ijob.infra.mapper.runtime.EventWaitMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

class EventWaitQueryServiceTest {
    @Test
    void namesUseLiteralSubstringAndOversizedInputFailsBeforeQuery() {
        var query = new com.ifelio.ijob.contract.event.EventWaitPageQuery();
        query.setTaskName("a!%_[b");
        assertEquals("%a!!!%!_![b%", query.getTaskNamePattern());
        query.setTaskName("x".repeat(129));
        var service = new EventWaitQueryService(mapper(approval()), new ObjectMapper());
        assertEquals(
                ResultCode.PARAM_ERROR,
                assertThrows(
                                BusinessException.class,
                                () -> service.queryApprovals(query, "reviewer"))
                        .getCode());
    }

    @Test
    void approvalDetailShouldRequireSnapshotOperator() {
        EventWait wait = approval();
        EventWaitQueryService service = new EventWaitQueryService(mapper(wait), new ObjectMapper());

        assertEquals(wait.getId(), service.get(wait.getId(), "reviewer").getId());
        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.get(wait.getId(), "intruder"));

        assertEquals(ResultCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void instanceLookupShouldApplySameApprovalPermission() {
        EventWait wait = approval();
        EventWaitQueryService service = new EventWaitQueryService(mapper(wait), new ObjectMapper());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.getByInstance(wait.getInstanceId(), "intruder"));

        assertEquals(ResultCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void mismatchedLegacyAndRelationalSnapshotsShouldFailClosed() {
        EventWait wait = approval();
        EventWaitQueryService service =
                new EventWaitQueryService(
                        mapper(wait, java.util.List.of("another-reviewer")), new ObjectMapper());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.get(wait.getId(), "reviewer"));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
    }

    private EventWait approval() {
        EventWait wait = new EventWait();
        wait.setId(5L);
        wait.setInstanceId(7L);
        wait.setRootInstanceId(7L);
        wait.setWaitType(EventWaitType.APPROVAL);
        wait.setStatus(EventWaitStatus.WAITING);
        wait.setAllowedOperatorIds("[\"reviewer\"]");
        return wait;
    }

    private EventWaitMapper mapper(EventWait wait) {
        return mapper(wait, java.util.List.of("reviewer"));
    }

    private EventWaitMapper mapper(EventWait wait, java.util.List<String> operators) {
        return (EventWaitMapper)
                Proxy.newProxyInstance(
                        EventWaitMapper.class.getClassLoader(),
                        new Class<?>[] {EventWaitMapper.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "selectById", "selectLatestByInstance" -> wait;
                                    case "selectOperatorIds" -> operators;
                                    case "selectContext" -> null;
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
