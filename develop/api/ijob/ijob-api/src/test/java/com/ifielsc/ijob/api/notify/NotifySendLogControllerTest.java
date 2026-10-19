package com.ifielsc.ijob.api.notify;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.notify.NotifySendLogDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.core.notify.NotifySendLogService;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotifySendLogControllerTest {
    @Test
    void queryShouldWrapContractPageAndDelegateFilters() {
        RecordingService service = new RecordingService();
        NotifySendLogController controller = new NotifySendLogController(service);
        NotifySendLogPageQuery query = new NotifySendLogPageQuery();

        ApiResponse<PageResult<NotifySendLogDTO>> response = controller.queryPage(query);

        assertTrue(response.isSuccess());
        assertSame(service.result, response.getData());
        assertSame(query, service.query);
    }

    private static final class RecordingService extends NotifySendLogService {
        private final PageResult<NotifySendLogDTO> result = pageResult();
        private NotifySendLogPageQuery query;

        private RecordingService() {
            super(null);
        }

        @Override
        public PageResult<NotifySendLogDTO> queryPage(NotifySendLogPageQuery selectedQuery) {
            query = selectedQuery;
            return result;
        }

        private static PageResult<NotifySendLogDTO> pageResult() {
            PageResult<NotifySendLogDTO> result = new PageResult<>();
            result.setList(List.of());
            result.setPageNum(1);
            result.setPageSize(10);
            return result;
        }
    }
}
