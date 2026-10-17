package com.ifelio.ijob.core.resource;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.resource.ReferenceSummaryDTO;
import com.ifelio.ijob.contract.resource.ReferenceSummaryRequest;
import com.ifelio.ijob.infra.mapper.notify.NotifyConfigMapper;
import com.ifelio.ijob.infra.mapper.resource.ResourceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferenceSummaryService {
    private final ResourceMapper resources;
    private final NotifyConfigMapper notifications;

    public List<ReferenceSummaryDTO> resolve(ReferenceSummaryRequest request) {
        if (request == null)
            throw BusinessException.localized(ResultCode.PARAM_ERROR, "References required");
        List<Long> resourceIds = validate(request.resourceIds());
        List<Long> notificationIds = validate(request.notificationIds());
        List<ReferenceSummaryDTO> result = new ArrayList<>();
        if (!resourceIds.isEmpty())
            resources
                    .selectSummariesByIds(resourceIds)
                    .forEach(
                            row ->
                                    result.add(
                                            new ReferenceSummaryDTO(
                                                    "RESOURCE",
                                                    row.getId().toString(),
                                                    row.getName(),
                                                    row.getType().name(),
                                                    row.getEnabled())));
        if (!notificationIds.isEmpty())
            notifications
                    .selectSummariesByIds(notificationIds)
                    .forEach(
                            row ->
                                    result.add(
                                            new ReferenceSummaryDTO(
                                                    "NOTIFICATION",
                                                    row.getId().toString(),
                                                    row.getName(),
                                                    row.getType().name(),
                                                    row.getEnabled())));
        return result;
    }

    private List<Long> validate(List<Long> ids) {
        if (ids == null) return List.of();
        if (ids.size() > 200 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "At most 200 positive reference IDs per kind");
        return ids.stream().distinct().toList();
    }
}
