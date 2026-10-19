// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceConnectionTestDTO;
import com.ifielsc.ijob.contract.resource.ResourceDTO;
import com.ifielsc.ijob.contract.resource.ResourceListDTO;
import com.ifielsc.ijob.contract.resource.ResourcePageQuery;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.resource.ResourceRuntimeService;
import com.ifielsc.ijob.core.resource.ResourceService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

class ResourceControllerTest {
    @Test
    void createShouldReturnCreatedResponseAndLocation() {
        RecordingResourceService service = new RecordingResourceService();
        ResourceController controller =
                new ResourceController(service, new RecordingRuntimeService());
        ResourceUpsertRequest request = new ResourceUpsertRequest();

        ResponseEntity<ApiResponse<String>> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(URI.create("/api/resources/88"), response.getHeaders().getLocation());
        assertTrue(response.getBody().isSuccess());
        assertEquals("88", response.getBody().getData());
        assertSame(request, service.createRequest);
    }

    @Test
    void queryAndDetailShouldWrapOnlyContractDtos() {
        RecordingResourceService service = new RecordingResourceService();
        ResourceController controller =
                new ResourceController(service, new RecordingRuntimeService());
        ResourcePageQuery query = new ResourcePageQuery();

        ApiResponse<PageResult<ResourceListDTO>> page = controller.queryPage(query);
        ApiResponse<ResourceDTO> detail = controller.getDetail(9L);

        assertTrue(page.isSuccess());
        assertSame(service.pageResult, page.getData());
        assertSame(query, service.pageQuery);
        assertTrue(detail.isSuccess());
        assertSame(service.detail, detail.getData());
        assertEquals(9L, service.detailId);
    }

    @Test
    void updateAndDeleteShouldDelegatePathId() {
        RecordingResourceService service = new RecordingResourceService();
        ResourceController controller =
                new ResourceController(service, new RecordingRuntimeService());
        ResourceUpsertRequest request = new ResourceUpsertRequest();

        ApiResponse<Void> update = controller.update(12L, request);
        ApiResponse<Void> delete = controller.delete(13L);

        assertTrue(update.isSuccess());
        assertEquals(12L, service.updateId);
        assertSame(request, service.updateRequest);
        assertTrue(delete.isSuccess());
        assertEquals(13L, service.deleteId);
    }

    @Test
    void connectionTestShouldBeExplicitAndReturnSafeContractDto() {
        RecordingRuntimeService runtime = new RecordingRuntimeService();
        ResourceController controller =
                new ResourceController(new RecordingResourceService(), runtime);

        ApiResponse<ResourceConnectionTestDTO> response = controller.testConnection(21L);

        assertTrue(response.isSuccess());
        assertEquals(21L, runtime.connectionTestId);
        assertEquals(ResourceType.DB, response.getData().getType());
        assertTrue(response.getData().isSuccessful());
    }

    private static final class RecordingResourceService extends ResourceService {
        private final PageResult<ResourceListDTO> pageResult = pageResult();
        private final ResourceDTO detail = new ResourceDTO();
        private ResourcePageQuery pageQuery;
        private Long detailId;
        private ResourceUpsertRequest createRequest;
        private Long updateId;
        private ResourceUpsertRequest updateRequest;
        private Long deleteId;

        private RecordingResourceService() {
            super(null, null, null, null, event -> {});
        }

        @Override
        public PageResult<ResourceListDTO> queryPage(ResourcePageQuery query) {
            pageQuery = query;
            return pageResult;
        }

        @Override
        public ResourceDTO getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public Long create(ResourceUpsertRequest request) {
            createRequest = request;
            return 88L;
        }

        @Override
        public void update(Long resourceId, ResourceUpsertRequest request) {
            updateId = resourceId;
            updateRequest = request;
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }

        private static PageResult<ResourceListDTO> pageResult() {
            PageResult<ResourceListDTO> result = new PageResult<>();
            result.setList(List.of());
            result.setPageNum(1);
            result.setPageSize(10);
            return result;
        }
    }

    private static final class RecordingRuntimeService extends ResourceRuntimeService {
        private Long connectionTestId;

        private RecordingRuntimeService() {
            super(null, null, List.of());
        }

        @Override
        public ResourceConnectionTestDTO testConnection(Long resourceId) {
            connectionTestId = resourceId;
            return new ResourceConnectionTestDTO(resourceId, ResourceType.DB, true);
        }
    }
}
