package com.ifelio.ijob.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.infra.entity.event.EventBinding;
import com.ifelio.ijob.infra.mapper.event.EventBindingMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

class EventBindingServiceTest {
    @Test
    void constructorInjectedMapperShouldServeEventLookup() {
        EventBinding binding = new EventBinding();
        binding.setId(7L);
        EventBindingMapper mapper =
                (EventBindingMapper)
                        Proxy.newProxyInstance(
                                EventBindingMapper.class.getClassLoader(),
                                new Class<?>[] {EventBindingMapper.class},
                                (proxy, method, args) -> {
                                    if ("selectListByEventCode".equals(method.getName())) {
                                        return List.of(binding);
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        EventBindingService service = new EventBindingService(mapper);

        assertEquals(List.of(binding), service.listBindingsByEventCode("ready"));
    }

    @Test
    void batchDeleteShouldRejectConcurrentAffectedRowMismatch() {
        EventBindingMapper mapper =
                (EventBindingMapper)
                        Proxy.newProxyInstance(
                                EventBindingMapper.class.getClassLoader(),
                                new Class<?>[] {EventBindingMapper.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "selectIdsForUpdate" -> List.of(1L, 2L);
                                            case "deleteByIds" -> 1;
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        EventBindingService service = new EventBindingService(mapper);
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(2L, 1L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteBatch(request));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
    }
}
