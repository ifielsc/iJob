package com.ifelio.ijob.api.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.schedule.ScheduleDTO;
import com.ifelio.ijob.contract.schedule.ScheduleEnabledRequest;
import com.ifelio.ijob.contract.schedule.SchedulePageQuery;
import com.ifelio.ijob.contract.schedule.SchedulePreviewDTO;
import com.ifelio.ijob.contract.schedule.SchedulePreviewRequest;
import com.ifelio.ijob.contract.schedule.ScheduleUpsertRequest;
import com.ifelio.ijob.core.schedule.ScheduleService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

class ScheduleControllerTest {
    @Test
    void controllerShouldDelegateCompleteManagementFlow() {
        RecordingScheduleService service = new RecordingScheduleService();
        ScheduleController controller = new ScheduleController(service);
        SchedulePageQuery query = new SchedulePageQuery();
        ScheduleUpsertRequest upsert = new ScheduleUpsertRequest();
        ScheduleEnabledRequest enabled = new ScheduleEnabledRequest();
        SchedulePreviewRequest preview = new SchedulePreviewRequest();

        ApiResponse<PageResult<ScheduleDTO>> page = controller.queryPage(query);
        ApiResponse<ScheduleDTO> detail = controller.getDetail(8L);
        ResponseEntity<ApiResponse<String>> created = controller.create(upsert);
        ApiResponse<Void> updated = controller.update(9L, upsert);
        ApiResponse<Void> patched = controller.updateEnabled(10L, enabled);
        ApiResponse<Void> deleted = controller.delete(11L);
        ApiResponse<SchedulePreviewDTO> previewed = controller.preview(preview);

        assertSame(service.page, page.getData());
        assertSame(query, service.query);
        assertSame(service.detail, detail.getData());
        assertEquals(8L, service.detailId);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("88", created.getBody().getData());
        assertEquals(URI.create("/api/schedules/88"), created.getHeaders().getLocation());
        assertSame(upsert, service.createRequest);
        assertEquals(9L, service.updateId);
        assertEquals(10L, service.enabledId);
        assertSame(enabled, service.enabledRequest);
        assertEquals(11L, service.deleteId);
        assertSame(service.previewResult, previewed.getData());
        assertSame(preview, service.previewRequest);
        assertTrue(updated.isSuccess());
        assertTrue(patched.isSuccess());
        assertTrue(deleted.isSuccess());
    }

    private static final class RecordingScheduleService extends ScheduleService {
        private final PageResult<ScheduleDTO> page = page();
        private final ScheduleDTO detail = new ScheduleDTO();
        private final SchedulePreviewDTO previewResult = new SchedulePreviewDTO();
        private SchedulePageQuery query;
        private Long detailId;
        private ScheduleUpsertRequest createRequest;
        private Long updateId;
        private Long enabledId;
        private ScheduleEnabledRequest enabledRequest;
        private Long deleteId;
        private SchedulePreviewRequest previewRequest;

        private RecordingScheduleService() {
            super(null, null, null, null, null);
        }

        @Override
        public PageResult<ScheduleDTO> queryPage(SchedulePageQuery selected) {
            query = selected;
            return page;
        }

        @Override
        public ScheduleDTO getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public Long create(ScheduleUpsertRequest request) {
            createRequest = request;
            return 88L;
        }

        @Override
        public void update(Long id, ScheduleUpsertRequest request) {
            updateId = id;
        }

        @Override
        public void updateEnabled(Long id, ScheduleEnabledRequest request) {
            enabledId = id;
            enabledRequest = request;
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }

        @Override
        public SchedulePreviewDTO preview(SchedulePreviewRequest request) {
            previewRequest = request;
            return previewResult;
        }

        private static PageResult<ScheduleDTO> page() {
            PageResult<ScheduleDTO> result = new PageResult<>();
            result.setList(List.of());
            return result;
        }
    }
}
