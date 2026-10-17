package com.ifelio.ijob.core.notify;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.notify.NotifyBindingCreateRequest;
import com.ifelio.ijob.contract.notify.NotifyBindingDTO;
import com.ifelio.ijob.contract.notify.NotifyBindingUpdateRequest;
import com.ifelio.ijob.core.management.BatchDeleteValidator;
import com.ifelio.ijob.infra.entity.notify.NotifyBinding;
import com.ifelio.ijob.infra.mapper.job.JobMapper;
import com.ifelio.ijob.infra.mapper.notify.NotifyBindingMapper;
import com.ifelio.ijob.infra.mapper.notify.NotifyConfigMapper;
import com.ifelio.ijob.infra.mapper.task.TaskMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotifyBindingService {
    private final NotifyBindingMapper notifyBindingMapper;
    private final NotifyConfigMapper notifyConfigMapper;
    private final TaskMapper taskMapper;
    private final JobMapper jobMapper;

    public List<NotifyBindingDTO> listDetailsByTarget(TargetType targetType, Long targetId) {
        requireTarget(targetType, targetId);
        return notifyBindingMapper.selectDtoListByTarget(targetType, targetId);
    }

    public NotifyBindingDTO getDetail(Long id) {
        requirePositiveId(id, "binding ID");
        NotifyBindingDTO detail = notifyBindingMapper.selectDtoById(id);
        if (detail == null) {
            throw notFound("notify binding does not exist");
        }
        return detail;
    }

    public List<NotifyBinding> listNotifyBindingsByTargetId(TargetType targetType, Long targetId) {
        return notifyBindingMapper.selectListByTargetId(targetType, targetId);
    }

    @Transactional
    public Long create(NotifyBindingCreateRequest request) {
        validateCreateRequest(request);
        requireTarget(request.getTargetType(), request.getTargetId());
        requireNotifyConfig(request.getNotifyConfigId());
        if (notifyBindingMapper.selectByTargetAndConfig(
                        request.getTargetType(), request.getTargetId(), request.getNotifyConfigId())
                != null) {
            throw conflict("notify binding already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        NotifyBinding binding = new NotifyBinding();
        binding.setTargetType(request.getTargetType());
        binding.setTargetId(request.getTargetId());
        binding.setNotifyConfigId(request.getNotifyConfigId());
        binding.setNotifyPolicy(request.getNotifyPolicy());
        binding.setCreateTime(now);
        binding.setUpdateTime(now);
        try {
            if (notifyBindingMapper.insert(binding) != 1 || binding.getId() == null) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "notify binding creation failed");
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("notify binding already exists");
        } catch (DataIntegrityViolationException exception) {
            throw conflict("notify binding references changed during creation");
        }
        log.info(
                "notify binding created, id={}, targetType={}, targetId={}",
                binding.getId(),
                binding.getTargetType(),
                binding.getTargetId());
        return binding.getId();
    }

    @Transactional
    public void update(Long id, NotifyBindingUpdateRequest request) {
        requirePositiveId(id, "binding ID");
        if (request == null || request.getNotifyPolicy() == null) {
            throw paramError("notify policy is required");
        }
        requireBinding(id);
        NotifyBinding update = new NotifyBinding();
        update.setId(id);
        update.setNotifyPolicy(request.getNotifyPolicy());
        update.setUpdateTime(LocalDateTime.now());
        if (notifyBindingMapper.updatePolicyById(update) != 1) {
            throw conflict("notify binding update failed");
        }
        log.info("notify binding updated, id={}, policy={}", id, request.getNotifyPolicy());
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
        List<Long> existing = notifyBindingMapper.selectIdsForUpdate(ids);
        if (existing == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "notify binding batch lock failed");
        }
        if (!existing.equals(ids)) {
            throw notFound("some notify bindings do not exist");
        }
        if (notifyBindingMapper.deleteByIds(ids) != ids.size()) {
            throw conflict("notify binding deletion conflict");
        }
        log.info("notify bindings deleted, count={}", ids.size());
    }

    @Transactional
    public void deleteBindingsByTarget(TargetType targetType, Long targetId) {
        notifyBindingMapper.deleteByTargetId(targetType, targetId);
    }

    @Transactional
    public void deleteBindingsByTargets(TargetType targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(targetIds));
        notifyBindingMapper.deleteByTargetIds(targetType, uniqueIds);
    }

    private void validateCreateRequest(NotifyBindingCreateRequest request) {
        if (request == null) {
            throw paramError("request body is required");
        }
        if (request.getTargetType() == null) {
            throw paramError("target type is required");
        }
        requirePositiveId(request.getTargetId(), "target ID");
        requirePositiveId(request.getNotifyConfigId(), "notify config ID");
        if (request.getNotifyPolicy() == null) {
            throw paramError("notify policy is required");
        }
    }

    private void requireTarget(TargetType targetType, Long targetId) {
        if (targetType == null) {
            throw paramError("target type is required");
        }
        requirePositiveId(targetId, "target ID");
        boolean exists =
                switch (targetType) {
                    case TASK -> taskMapper.selectById(targetId) != null;
                    case JOB -> jobMapper.selectById(targetId) != null;
                };
        if (!exists) {
            throw notFound("notify binding target does not exist");
        }
    }

    private void requireNotifyConfig(Long notifyConfigId) {
        if (notifyConfigMapper.countById(notifyConfigId) < 1) {
            throw notFound("notify config does not exist");
        }
    }

    private NotifyBinding requireBinding(Long id) {
        NotifyBinding binding = notifyBindingMapper.selectById(id);
        if (binding == null) {
            throw notFound("notify binding does not exist");
        }
        return binding;
    }

    private void requirePositiveId(Long id, String field) {
        if (id == null || id <= 0) {
            throw paramError(field + " must be greater than 0");
        }
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    private BusinessException notFound(String message) {
        return BusinessException.localized(ResultCode.NOT_FOUND, message);
    }

    private BusinessException conflict(String message) {
        return BusinessException.localized(ResultCode.CONFLICT, message);
    }
}
