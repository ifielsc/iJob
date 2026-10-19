// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.runtime.InstanceDTO;
import com.ifielsc.ijob.contract.runtime.InstanceLogDTO;
import com.ifielsc.ijob.contract.runtime.InstancePageQuery;
import com.ifielsc.ijob.contract.runtime.RunTreeDTO;
import com.ifielsc.ijob.contract.runtime.TriggerDTO;
import com.ifielsc.ijob.contract.runtime.TriggerPageQuery;
import com.ifielsc.ijob.core.instance.RuntimeQueryService;

import org.junit.jupiter.api.Test;

import java.util.List;

class RuntimeQueryControllerTest {
    @Test
    void shouldWrapRuntimeContractDtos() {
        RecordingService service = new RecordingService();
        RuntimeQueryController controller =
                new RuntimeQueryController(
                        service,
                        new RuntimeMessageLocalizer(
                                new org.springframework.context.support.StaticMessageSource()),
                        null);
        InstancePageQuery query = new InstancePageQuery();
        TriggerPageQuery triggerQuery = new TriggerPageQuery();

        ApiResponse<TriggerDTO> trigger = controller.trigger(1L);
        ApiResponse<PageResult<TriggerDTO>> triggers = controller.triggers(triggerQuery);
        ApiResponse<PageResult<InstanceDTO>> page = controller.instances(query);
        ApiResponse<InstanceDTO> instance = controller.instance(2L);
        ApiResponse<List<InstanceLogDTO>> logs = controller.logs(2L, 9L, 20);
        ApiResponse<RunTreeDTO> tree = controller.tree(2L, false);

        assertTrue(trigger.isSuccess());
        assertSame(service.trigger, trigger.getData());
        assertSame(service.triggerPage, triggers.getData());
        assertSame(service.page, page.getData());
        assertSame(service.instance, instance.getData());
        assertSame(service.logs, logs.getData());
        assertSame(service.tree, tree.getData());
        assertSame(query, service.query);
        assertEquals(2L, service.instanceId);
        assertEquals(9L, service.afterId);
        assertEquals(20, service.limit);
    }

    private static final class RecordingService extends RuntimeQueryService {
        private final TriggerDTO trigger = new TriggerDTO();
        private final PageResult<InstanceDTO> page = new PageResult<>();
        private final PageResult<TriggerDTO> triggerPage = new PageResult<>();
        private final RunTreeDTO tree = new RunTreeDTO();
        private final InstanceDTO instance = new InstanceDTO();
        private final List<InstanceLogDTO> logs = List.of();
        private InstancePageQuery query;
        private Long instanceId;
        private Long afterId;
        private Integer limit;

        private RecordingService() {
            super(
                    null,
                    null,
                    null,
                    (com.ifielsc.ijob.infra.mapper.runtime.DagGatewayDecisionMapper)
                            java.lang.reflect.Proxy.newProxyInstance(
                                    RuntimeQueryControllerTest.class.getClassLoader(),
                                    new Class<?>[] {
                                        com.ifielsc.ijob.infra.mapper.runtime
                                                .DagGatewayDecisionMapper.class
                                    },
                                    (proxy, method, args) -> {
                                        throw new AssertionError(
                                                "Controller stub must not query persistence");
                                    }));
        }

        @Override
        public TriggerDTO getTrigger(Long id) {
            return trigger;
        }

        @Override
        public PageResult<TriggerDTO> queryTriggers(TriggerPageQuery query) {
            return triggerPage;
        }

        @Override
        public PageResult<InstanceDTO> queryInstances(InstancePageQuery query) {
            this.query = query;
            return page;
        }

        @Override
        public InstanceDTO getInstance(Long id) {
            instanceId = id;
            return instance;
        }

        @Override
        public List<InstanceLogDTO> listLogs(Long instanceId, Long afterId, Integer limit) {
            this.instanceId = instanceId;
            this.afterId = afterId;
            this.limit = limit;
            return logs;
        }

        @Override
        public RunTreeDTO getRunTree(Long id, boolean includeVariables) {
            return tree;
        }
    }
}
