package com.ifielsc.ijob.core.job;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.job.JobDetailResponse;
import com.ifielsc.ijob.contract.job.JobExecutionContextDTO;
import com.ifielsc.ijob.contract.job.JobValidationDTO;
import com.ifielsc.ijob.contract.job.JobValidationRequest;
import com.ifielsc.ijob.contract.job.JobVersionDTO;
import com.ifielsc.ijob.core.job.definition.JobDefinitionValidator;
import com.ifielsc.ijob.core.job.definition.NestedJobReferenceValidator;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionReferenceSnapshotMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDesignMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDesignReference;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobDesignService {
    private final JobService jobs;
    private final JobDesignMapper mapper;
    private final JobCurrentDefinitionMapper identities;
    private final JobDefinitionValidator validator;
    private final NestedJobReferenceValidator nesting;
    private final JobDefinitionReferenceSnapshotMapper snapshots;

    @Transactional(readOnly = true)
    public JobDetailResponse detail(Long id, Long definitionId) {
        JobDetailResponse result =
                definitionId == null ? jobs.getDetail(id) : jobs.getVersionDetail(id, definitionId);
        Map<String, JobDesignReference> oldCache = new HashMap<>(), currentCache = new HashMap<>();
        for (var node : result.getNodes()) {
            if (node.getKind() == JobNodeKind.GATEWAY) continue;
            String oldKey = node.getNodeType() + ":" + node.getRefDefinitionId();
            var old =
                    oldCache.computeIfAbsent(
                            oldKey,
                            key ->
                                    mapper.selectSnapshot(
                                            node.getNodeType(), node.getRefDefinitionId()));
            if (old == null || !old.getId().equals(node.getRefId()))
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            node.setRefName(old.getName());
            node.setRefRevision(old.getRevision());
            String key = node.getNodeType() + ":" + node.getRefId();
            var current =
                    currentCache.computeIfAbsent(
                            key, k -> mapper.selectCurrent(node.getNodeType(), node.getRefId()));
            node.setReferenceAvailable(current != null);
            if (current != null) {
                node.setCurrentRefName(current.getName());
                node.setCurrentRefDefinitionId(current.getDefinitionId());
                node.setCurrentRefRevision(current.getRevision());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<JobVersionDTO> versions(Long id, BasePageQuery query) {
        var identity = identities.selectById(id);
        if (identity == null) throw new BusinessException(ResultCode.NOT_FOUND);
        PageHelper.startPage(query.getPageNo(), query.getPageSize());
        try {
            var page = PageResults.from(mapper.selectVersions(id));
            var result = new PageResult<JobVersionDTO>();
            result.setList(
                    page.getList().stream()
                            .map(
                                    d ->
                                            new JobVersionDTO(
                                                    id.toString(),
                                                    d.getId().toString(),
                                                    d.getRevision().toString(),
                                                    d.getName(),
                                                    d.getEnabled(),
                                                    d.getCreateTime(),
                                                    d.getId()
                                                            .equals(
                                                                    identity
                                                                            .getCurrentDefinitionId())))
                            .toList());
            result.setTotal(page.getTotal());
            result.setPageNum(page.getPageNum());
            result.setPageSize(page.getPageSize());
            result.setPages(page.getPages());
            return result;
        } finally {
            PageHelper.clearPage();
        }
    }

    public JobExecutionContextDTO context(Long id) {
        var row = mapper.selectCurrent(TargetType.JOB, id);
        if (row == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return new JobExecutionContextDTO(
                id.toString(),
                row.getName(),
                row.getDefinitionId().toString(),
                row.getRevision().toString(),
                row.getEnabled());
    }

    public PageResult<String> groups(BasePageQuery query, String prefix) {
        String value = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9._-]{0,64}"))
            throw new BusinessException(ResultCode.PARAM_ERROR);
        PageHelper.startPage(query.getPageNo(), Math.min(query.getPageSize(), 50));
        try {
            return PageResults.from(mapper.selectGroups(value.replace("_", "!_") + "%"));
        } finally {
            PageHelper.clearPage();
        }
    }

    /** Read-only preflight. Publication always repeats validation under its own locks. */
    @Transactional(readOnly = true)
    public JobValidationDTO validate(JobValidationRequest request) {
        if (request == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        List<JobValidationDTO.Reference> references = new ArrayList<>();
        String path = "definition";
        try {
            validator.validate(request.getDefinition());
            if (request.getJobId() != null) {
                path = "expectedRevision";
                var current = mapper.selectCurrent(TargetType.JOB, request.getJobId());
                if (current == null) throw new BusinessException(ResultCode.NOT_FOUND);
                if (!current.getRevision().equals(request.getExpectedRevision()))
                    throw new BusinessException(ResultCode.CONFLICT);
            }
            for (int i = 0; i < request.getDefinition().getNodes().size(); i++) {
                var node = request.getDefinition().getNodes().get(i);
                if (node.getKind() == JobNodeKind.GATEWAY) continue;
                path = "nodes[" + i + "].refId";
                var ref = mapper.selectCurrent(node.getNodeType(), node.getRefId());
                if (ref == null) throw new BusinessException(ResultCode.NOT_FOUND);
                references.add(
                        new JobValidationDTO.Reference(
                                node.getNodeId(),
                                node.getNodeType().name(),
                                node.getRefId().toString(),
                                ref.getName(),
                                ref.getDefinitionId().toString(),
                                ref.getRevision().toString()));
            }
            path = "nodes";
            Map<Long, Set<Long>> graph = new HashMap<>();
            snapshots.selectAllCurrentJobIds().forEach(id -> graph.put(id, new HashSet<>()));
            snapshots
                    .selectCurrentJobReferences()
                    .forEach(
                            ref -> {
                                if (graph.containsKey(ref.jobId()))
                                    graph.get(ref.jobId()).add(ref.referencedJobId());
                            });
            nesting.validate(request.getJobId(), request.getDefinition().getNodes(), graph);
            return new JobValidationDTO(true, List.of(), references);
        } catch (BusinessException error) {
            if (error.getCode() == ResultCode.SYSTEM_ERROR) throw error;
            return new JobValidationDTO(
                    false,
                    List.of(
                            new JobValidationDTO.Issue(
                                    path,
                                    error.getErrorCode(),
                                    error.getMessageKey(),
                                    error.getMessageArgs(),
                                    null)),
                    references);
        }
    }
}
