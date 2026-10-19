package com.ifielsc.ijob.api.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.job.JobDetailResponse;
import com.ifielsc.ijob.contract.job.JobListDTO;
import com.ifielsc.ijob.contract.job.JobPageQuery;
import com.ifielsc.ijob.contract.job.JobPublicationDTO;
import com.ifielsc.ijob.contract.job.JobUpsertRequest;
import com.ifielsc.ijob.contract.runtime.ManualTriggerRequest;
import com.ifielsc.ijob.core.job.JobService;
import com.ifielsc.ijob.core.trigger.ManualJobTriggerService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

class JobControllerTest {
    @Test
    void createShouldReturnPublicationContractAndLocation() {
        RecordingJobService service = new RecordingJobService();
        JobController controller = controller(service);
        JobUpsertRequest request = new JobUpsertRequest();

        ResponseEntity<ApiResponse<JobPublicationDTO>> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(URI.create("/api/jobs/88"), response.getHeaders().getLocation());
        assertTrue(response.getBody().isSuccess());
        assertEquals(88L, response.getBody().getData().jobId());
        assertEquals(1L, response.getBody().getData().revision());
        assertSame(request, service.createRequest);
    }

    @Test
    void queryAndDetailShouldUseCurrentDefinitionFacade() {
        RecordingJobService service = new RecordingJobService();
        JobController controller = controller(service);
        JobPageQuery query = new JobPageQuery();

        ApiResponse<PageResult<JobListDTO>> page = controller.queryPage(query);
        ApiResponse<JobDetailResponse> detail = controller.getDetail(9L);

        assertSame(service.pageResult, page.getData());
        assertSame(query, service.pageQuery);
        assertSame(service.detail, detail.getData());
        assertEquals(9L, service.detailId);
    }

    @Test
    void updateAndDeleteShouldDelegateVersionedManagement() {
        RecordingJobService service = new RecordingJobService();
        JobController controller = controller(service);
        JobUpsertRequest request = new JobUpsertRequest();

        ApiResponse<JobPublicationDTO> update = controller.update(12L, 4L, request);
        ApiResponse<Void> delete = controller.delete(13L);

        assertEquals(5L, update.getData().revision());
        assertEquals(12L, service.updateId);
        assertEquals(4L, service.expectedRevision);
        assertSame(request, service.updateRequest);
        assertTrue(delete.isSuccess());
        assertEquals(13L, service.deleteId);
    }

    @Test
    void manualTriggerShouldReturnAcceptedTriggerLocation() {
        RecordingManualJobTriggerService manual = new RecordingManualJobTriggerService();
        JobController controller = new JobController(new RecordingJobService(), manual, null);

        ResponseEntity<ApiResponse<String>> response = controller.manualTrigger(21L, null);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(URI.create("/api/triggers/99"), response.getHeaders().getLocation());
        assertEquals("99", response.getBody().getData());
        assertEquals(21L, manual.jobId);
    }

    private JobController controller(RecordingJobService service) {
        return new JobController(
                service,
                new RecordingManualJobTriggerService(),
                new com.ifielsc.ijob.core.job.JobDesignService(
                        service, null, null, null, null, null) {
                    @Override
                    public JobDetailResponse detail(Long id, Long definitionId) {
                        return service.getDetail(id);
                    }
                });
    }

    private static final class RecordingJobService extends JobService {
        private final PageResult<JobListDTO> pageResult = pageResult();
        private final JobDetailResponse detail = new JobDetailResponse();
        private JobPageQuery pageQuery;
        private Long detailId;
        private JobUpsertRequest createRequest;
        private Long updateId;
        private Long expectedRevision;
        private JobUpsertRequest updateRequest;
        private Long deleteId;

        private RecordingJobService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public PageResult<JobListDTO> queryPage(JobPageQuery query) {
            pageQuery = query;
            return pageResult;
        }

        @Override
        public JobDetailResponse getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public JobPublicationDTO create(JobUpsertRequest request) {
            createRequest = request;
            return new JobPublicationDTO(88L, 188L, 1L);
        }

        @Override
        public JobPublicationDTO update(Long jobId, Long expected, JobUpsertRequest request) {
            updateId = jobId;
            expectedRevision = expected;
            updateRequest = request;
            return new JobPublicationDTO(jobId, 189L, expected + 1);
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }

        private static PageResult<JobListDTO> pageResult() {
            PageResult<JobListDTO> result = new PageResult<>();
            result.setList(List.of());
            result.setPageNum(1);
            result.setPageSize(10);
            return result;
        }
    }

    private static final class RecordingManualJobTriggerService extends ManualJobTriggerService {
        private Long jobId;

        private RecordingManualJobTriggerService() {
            super(null, null, null, null);
        }

        @Override
        public Long trigger(Long id, ManualTriggerRequest request) {
            jobId = id;
            return 99L;
        }
    }
}
