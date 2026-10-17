package com.ifelio.ijob.api.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.auth.LoginResponse;
import com.ifelio.ijob.contract.notify.NotifyConfigDTO;
import com.ifelio.ijob.contract.notify.NotifyConfigListDTO;
import com.ifelio.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifelio.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifelio.ijob.contract.notify.NotifyTestSendDTO;
import com.ifelio.ijob.contract.notify.NotifyTestSendRequest;
import com.ifelio.ijob.core.auth.AuthSession;
import com.ifelio.ijob.core.notify.NotifyConfigService;
import com.ifelio.ijob.core.notify.NotifyTestSendService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

class NotifyConfigControllerTest {
    @Test
    void createShouldReturnCreatedResponseAndLocation() {
        RecordingNotifyConfigService service = new RecordingNotifyConfigService();
        RecordingNotifyTestSendService testSendService = new RecordingNotifyTestSendService();
        NotifyConfigController controller =
                new NotifyConfigController(service, testSendService, new FakeAuthSession());
        NotifyConfigUpsertRequest request = new NotifyConfigUpsertRequest();

        ResponseEntity<ApiResponse<String>> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(URI.create("/api/notify-configs/66"), response.getHeaders().getLocation());
        assertTrue(response.getBody().isSuccess());
        assertEquals("66", response.getBody().getData());
        assertSame(request, service.createRequest);
    }

    @Test
    void queryAndDetailShouldWrapOnlyContractDtos() {
        RecordingNotifyConfigService service = new RecordingNotifyConfigService();
        RecordingNotifyTestSendService testSendService = new RecordingNotifyTestSendService();
        NotifyConfigController controller =
                new NotifyConfigController(service, testSendService, new FakeAuthSession());
        NotifyConfigPageQuery query = new NotifyConfigPageQuery();

        ApiResponse<PageResult<NotifyConfigListDTO>> page = controller.queryPage(query);
        ApiResponse<NotifyConfigDTO> detail = controller.getDetail(9L);

        assertTrue(page.isSuccess());
        assertSame(service.pageResult, page.getData());
        assertSame(query, service.pageQuery);
        assertTrue(detail.isSuccess());
        assertSame(service.detail, detail.getData());
        assertEquals(9L, service.detailId);
    }

    @Test
    void updateAndDeleteShouldDelegatePathId() {
        RecordingNotifyConfigService service = new RecordingNotifyConfigService();
        RecordingNotifyTestSendService testSendService = new RecordingNotifyTestSendService();
        NotifyConfigController controller =
                new NotifyConfigController(service, testSendService, new FakeAuthSession());
        NotifyConfigUpsertRequest request = new NotifyConfigUpsertRequest();

        ApiResponse<Void> update = controller.update(12L, request);
        ApiResponse<Void> delete = controller.delete(13L);

        assertTrue(update.isSuccess());
        assertEquals(12L, service.updateId);
        assertSame(request, service.updateRequest);
        assertTrue(delete.isSuccess());
        assertEquals(13L, service.deleteId);
    }

    @Test
    void testSendShouldWrapContractResult() {
        RecordingNotifyConfigService service = new RecordingNotifyConfigService();
        RecordingNotifyTestSendService testSendService = new RecordingNotifyTestSendService();
        NotifyConfigController controller =
                new NotifyConfigController(service, testSendService, new FakeAuthSession());
        NotifyTestSendRequest request = new NotifyTestSendRequest();

        ApiResponse<NotifyTestSendDTO> response = controller.testSend(19L, request);

        assertTrue(response.isSuccess());
        assertSame(testSendService.result, response.getData());
        assertEquals(19L, testSendService.notifyConfigId);
        assertSame(request, testSendService.request);
        assertEquals("operator-19", testSendService.operatorId);
    }

    private static final class RecordingNotifyConfigService extends NotifyConfigService {
        private final PageResult<NotifyConfigListDTO> pageResult = pageResult();
        private final NotifyConfigDTO detail = new NotifyConfigDTO();
        private NotifyConfigPageQuery pageQuery;
        private Long detailId;
        private NotifyConfigUpsertRequest createRequest;
        private Long updateId;
        private NotifyConfigUpsertRequest updateRequest;
        private Long deleteId;

        private RecordingNotifyConfigService() {
            super(null, null, null, null);
        }

        @Override
        public PageResult<NotifyConfigListDTO> queryPage(NotifyConfigPageQuery query) {
            pageQuery = query;
            return pageResult;
        }

        @Override
        public NotifyConfigDTO getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public Long create(NotifyConfigUpsertRequest request) {
            createRequest = request;
            return 66L;
        }

        @Override
        public void update(Long id, NotifyConfigUpsertRequest request) {
            updateId = id;
            updateRequest = request;
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }

        private static PageResult<NotifyConfigListDTO> pageResult() {
            PageResult<NotifyConfigListDTO> result = new PageResult<>();
            result.setList(List.of());
            result.setPageNum(1);
            result.setPageSize(10);
            return result;
        }
    }

    private static final class RecordingNotifyTestSendService extends NotifyTestSendService {
        private final NotifyTestSendDTO result = new NotifyTestSendDTO();
        private Long notifyConfigId;
        private NotifyTestSendRequest request;
        private String operatorId;

        private RecordingNotifyTestSendService() {
            super(null);
        }

        @Override
        public NotifyTestSendDTO testSend(
                Long dispatchedConfigId,
                NotifyTestSendRequest dispatchedRequest,
                String dispatchedOperatorId) {
            notifyConfigId = dispatchedConfigId;
            request = dispatchedRequest;
            operatorId = dispatchedOperatorId;
            return result;
        }
    }

    private static final class FakeAuthSession implements AuthSession {
        @Override
        public LoginResponse login(String username) {
            return null;
        }

        @Override
        public CurrentUserResponse currentUser() {
            return new CurrentUserResponse("operator-19", "operator");
        }

        @Override
        public void logout() {}
    }
}
