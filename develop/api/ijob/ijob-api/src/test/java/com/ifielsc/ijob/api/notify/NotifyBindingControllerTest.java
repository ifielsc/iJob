// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifyBindingCreateRequest;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.notify.NotifyBindingUpdateRequest;
import com.ifielsc.ijob.core.notify.NotifyBindingService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

class NotifyBindingControllerTest {
    @Test
    void createShouldReturnCreatedResponseAndLocation() {
        RecordingNotifyBindingService service = new RecordingNotifyBindingService();
        NotifyBindingController controller = new NotifyBindingController(service);
        NotifyBindingCreateRequest request = new NotifyBindingCreateRequest();

        ResponseEntity<ApiResponse<String>> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(
                URI.create("/api/notify-bindings/9223372036854775807"),
                response.getHeaders().getLocation());
        assertTrue(response.getBody().isSuccess());
        assertEquals("9223372036854775807", response.getBody().getData());
        assertSame(request, service.createRequest);
    }

    @Test
    void listAndDetailShouldWrapOnlyContractDtos() {
        RecordingNotifyBindingService service = new RecordingNotifyBindingService();
        NotifyBindingController controller = new NotifyBindingController(service);

        ApiResponse<List<NotifyBindingDTO>> list =
                controller.listDetailsByTarget(TargetType.TASK, 8L);
        ApiResponse<NotifyBindingDTO> detail = controller.getDetail(9L);

        assertTrue(list.isSuccess());
        assertSame(service.details, list.getData());
        assertEquals(TargetType.TASK, service.targetType);
        assertEquals(8L, service.targetId);
        assertTrue(detail.isSuccess());
        assertSame(service.detail, detail.getData());
        assertEquals(9L, service.detailId);
    }

    @Test
    void updateAndDeleteShouldDelegatePathId() {
        RecordingNotifyBindingService service = new RecordingNotifyBindingService();
        NotifyBindingController controller = new NotifyBindingController(service);
        NotifyBindingUpdateRequest request = new NotifyBindingUpdateRequest();

        ApiResponse<Void> update = controller.update(12L, request);
        ApiResponse<Void> delete = controller.delete(13L);

        assertTrue(update.isSuccess());
        assertEquals(12L, service.updateId);
        assertSame(request, service.updateRequest);
        assertTrue(delete.isSuccess());
        assertEquals(13L, service.deleteId);
    }

    private static final class RecordingNotifyBindingService extends NotifyBindingService {
        private final List<NotifyBindingDTO> details = List.of(new NotifyBindingDTO());
        private final NotifyBindingDTO detail = new NotifyBindingDTO();
        private TargetType targetType;
        private Long targetId;
        private Long detailId;
        private NotifyBindingCreateRequest createRequest;
        private Long updateId;
        private NotifyBindingUpdateRequest updateRequest;
        private Long deleteId;

        private RecordingNotifyBindingService() {
            super(null, null, null, null);
        }

        @Override
        public List<NotifyBindingDTO> listDetailsByTarget(
                TargetType resolvedTargetType, Long resolvedTargetId) {
            targetType = resolvedTargetType;
            targetId = resolvedTargetId;
            return details;
        }

        @Override
        public NotifyBindingDTO getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public Long create(NotifyBindingCreateRequest request) {
            createRequest = request;
            return Long.MAX_VALUE;
        }

        @Override
        public void update(Long id, NotifyBindingUpdateRequest request) {
            updateId = id;
            updateRequest = request;
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }
    }
}
