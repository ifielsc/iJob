package com.ifelio.ijob.api.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.calendar.CalendarDetailDTO;
import com.ifelio.ijob.contract.calendar.CalendarListDTO;
import com.ifelio.ijob.contract.calendar.CalendarPageQuery;
import com.ifelio.ijob.contract.calendar.CalendarPreviewDayDTO;
import com.ifelio.ijob.contract.calendar.CalendarUpsertRequest;
import com.ifelio.ijob.core.calendar.CalendarService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

class CalendarControllerTest {
    @Test
    void createShouldReturnCreatedResponseAndLocation() {
        RecordingCalendarService service = new RecordingCalendarService();
        CalendarController controller = new CalendarController(service);
        CalendarUpsertRequest request = new CalendarUpsertRequest();

        ResponseEntity<ApiResponse<String>> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(URI.create("/api/calendars/88"), response.getHeaders().getLocation());
        assertEquals("88", response.getBody().getData());
        assertSame(request, service.createRequest);
    }

    @Test
    void queryAndDetailShouldWrapContractDtos() {
        RecordingCalendarService service = new RecordingCalendarService();
        CalendarController controller = new CalendarController(service);
        CalendarPageQuery query = new CalendarPageQuery();

        ApiResponse<PageResult<CalendarListDTO>> page = controller.queryPage(query);
        ApiResponse<CalendarDetailDTO> detail = controller.getDetail(9L);

        assertTrue(page.isSuccess());
        assertSame(service.pageResult, page.getData());
        assertSame(query, service.pageQuery);
        assertTrue(detail.isSuccess());
        assertSame(service.detail, detail.getData());
        assertEquals(9L, service.detailId);
    }

    @Test
    void updateAndDeleteShouldDelegatePathId() {
        RecordingCalendarService service = new RecordingCalendarService();
        CalendarController controller = new CalendarController(service);
        CalendarUpsertRequest request = new CalendarUpsertRequest();

        ApiResponse<Void> update = controller.update(12L, request);
        ApiResponse<Void> delete = controller.delete(13L);

        assertTrue(update.isSuccess());
        assertEquals(12L, service.updateId);
        assertSame(request, service.updateRequest);
        assertTrue(delete.isSuccess());
        assertEquals(13L, service.deleteId);
    }

    @Test
    void previewShouldDelegateRangeAndWrapContractDays() {
        RecordingCalendarService service = new RecordingCalendarService();
        CalendarController controller = new CalendarController(service);
        LocalDate from = LocalDate.of(2026, 8, 15);

        ApiResponse<List<CalendarPreviewDayDTO>> response = controller.preview(9L, from, 14);

        assertSame(service.preview, response.getData());
        assertEquals(9L, service.previewId);
        assertEquals(from, service.previewFrom);
        assertEquals(14, service.previewDays);
    }

    private static final class RecordingCalendarService extends CalendarService {
        private final PageResult<CalendarListDTO> pageResult = pageResult();
        private final CalendarDetailDTO detail = new CalendarDetailDTO();
        private CalendarPageQuery pageQuery;
        private Long detailId;
        private CalendarUpsertRequest createRequest;
        private Long updateId;
        private CalendarUpsertRequest updateRequest;
        private Long deleteId;
        private final List<CalendarPreviewDayDTO> preview = List.of();
        private Long previewId;
        private LocalDate previewFrom;
        private Integer previewDays;

        private RecordingCalendarService() {
            super(null, null, null, event -> {});
        }

        @Override
        public PageResult<CalendarListDTO> queryPage(CalendarPageQuery query) {
            pageQuery = query;
            return pageResult;
        }

        @Override
        public CalendarDetailDTO getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public Long create(CalendarUpsertRequest request) {
            createRequest = request;
            return 88L;
        }

        @Override
        public void update(Long id, CalendarUpsertRequest request) {
            updateId = id;
            updateRequest = request;
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }

        @Override
        public List<CalendarPreviewDayDTO> preview(Long id, LocalDate from, Integer days) {
            previewId = id;
            previewFrom = from;
            previewDays = days;
            return preview;
        }

        private static PageResult<CalendarListDTO> pageResult() {
            PageResult<CalendarListDTO> result = new PageResult<>();
            result.setList(List.of());
            result.setPageNum(1);
            result.setPageSize(10);
            return result;
        }
    }
}
