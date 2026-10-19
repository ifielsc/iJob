// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigListDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifielsc.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.core.task.definition.DataCheckReferences;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.mapper.notify.NotifyBindingMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyConfigMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotifyConfigService {
    private static final int MAX_NAME_LENGTH = 128;

    private final NotifyConfigMapper notifyConfigMapper;
    private final NotifyBindingMapper notifyBindingMapper;
    private final NotifyConfigValueService valueService;
    private final DataCheckReferences dataCheckReferences;

    public PageResult<NotifyConfigListDTO> queryPage(NotifyConfigPageQuery query) {
        NotifyConfigPageQuery resolvedQuery = query == null ? new NotifyConfigPageQuery() : query;
        PageHelper.startPage(resolvedQuery.getPageNo(), resolvedQuery.getPageSize());
        try {
            return PageResults.from(notifyConfigMapper.selectDtoList(resolvedQuery));
        } finally {
            PageHelper.clearPage();
        }
    }

    public NotifyConfigDTO getDetail(Long id) {
        NotifyConfig config = requireNotifyConfig(id);
        NotifyConfigDTO dto = new NotifyConfigDTO();
        BeanUtils.copyProperties(config, dto);
        dto.setConfig(valueService.view(config));
        return dto;
    }

    public NotifyConfig getNotifyConfig(Long id) {
        NotifyConfig stored = notifyConfigMapper.selectById(id);
        return decryptSnapshot(stored);
    }

    public NotifyConfig decryptSnapshot(NotifyConfig stored) {
        if (stored == null) {
            return null;
        }
        NotifyConfig runtime = new NotifyConfig();
        BeanUtils.copyProperties(stored, runtime);
        runtime.setConfig(valueService.decryptForRuntime(stored));
        return runtime;
    }

    @Transactional
    public Long create(NotifyConfigUpsertRequest request) {
        validateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        NotifyConfig config = toEntity(request);
        config.setConfig(valueService.protect(request, null));
        config.setCreateTime(now);
        config.setUpdateTime(now);
        if (notifyConfigMapper.insert(config) != 1 || config.getId() == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "notify config creation failed");
        }
        log.info("notify config created, id={}, type={}", config.getId(), config.getType());
        return config.getId();
    }

    @Transactional
    public void update(Long id, NotifyConfigUpsertRequest request) {
        validateRequest(request);
        if ((request.getEmailConfig() != null
                        && Boolean.TRUE.equals(request.getEmailConfig().getPreserveExisting()))
                || (request.getDingTalkConfig() != null
                        && Boolean.TRUE.equals(
                                request.getDingTalkConfig().getPreserveExisting()))) {
            notifyConfigMapper.selectIdsForUpdate(List.of(id));
        }
        NotifyConfig existing = requireNotifyConfig(id);
        NotifyConfig config = toEntity(request);
        config.setId(id);
        config.setConfig(valueService.protect(request, existing));
        config.setUpdateTime(LocalDateTime.now());
        if (notifyConfigMapper.updateById(config) != 1) {
            throw BusinessException.localized(ResultCode.CONFLICT, "notify config update failed");
        }
        log.info("notify config updated, id={}, type={}", id, config.getType());
    }

    @Transactional
    public void delete(Long id) {
        deleteIds(BatchDeleteValidator.validate(BatchDeleteValidator.single(id)));
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        deleteIds(BatchDeleteValidator.validate(request));
    }

    private void deleteIds(List<Long> ids) {
        List<Long> existing = notifyConfigMapper.selectIdsForUpdate(ids);
        if (existing == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "notify config batch lock failed");
        }
        if (!existing.equals(ids)) {
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "some notify configs do not exist");
        }
        if (notifyBindingMapper.countByNotifyConfigIds(ids) > 0) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "notify config is still referenced by bindings");
        }
        dataCheckReferences.requireUnreferenced(ids);
        if (notifyConfigMapper.deleteByIds(ids) != ids.size()) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "notify config deletion conflict");
        }
        log.info("notify configs deleted, count={}", ids.size());
    }

    private NotifyConfig requireNotifyConfig(Long id) {
        requirePositiveId(id);
        NotifyConfig config = notifyConfigMapper.selectById(id);
        if (config == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "notify config does not exist");
        }
        return config;
    }

    private void validateRequest(NotifyConfigUpsertRequest request) {
        if (request == null) {
            throw paramError("request body is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw paramError("notify config name is required");
        }
        if (request.getName().trim().length() > MAX_NAME_LENGTH) {
            throw paramError("notify config name cannot exceed 128 characters");
        }
        if (request.getType() == null) {
            throw paramError("notify type is required");
        }
    }

    private NotifyConfig toEntity(NotifyConfigUpsertRequest request) {
        NotifyConfig config = new NotifyConfig();
        config.setName(request.getName().trim());
        config.setType(request.getType());
        config.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return config;
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw paramError("notify config ID must be greater than 0");
        }
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }
}
