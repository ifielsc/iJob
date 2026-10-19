// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.event;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.event.EventBindingCreateRequest;
import com.ifielsc.ijob.contract.event.EventBindingDTO;
import com.ifielsc.ijob.contract.event.EventBindingUpdateRequest;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.infra.entity.event.EventBinding;
import com.ifielsc.ijob.infra.mapper.event.EventBindingMapper;
import com.ifielsc.ijob.infra.mapper.job.JobMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EventBindingService {
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final EventBindingMapper eventBindingMapper;
    private final TaskMapper taskMapper;
    private final JobMapper jobMapper;
    private final com.ifielsc.ijob.core.task.definition.TaskAdmissionResolver taskAdmission;
    private final com.ifielsc.ijob.core.job.definition.JobAdmissionResolver jobAdmission;

    @Autowired
    public EventBindingService(
            EventBindingMapper eventBindingMapper,
            TaskMapper taskMapper,
            JobMapper jobMapper,
            com.ifielsc.ijob.core.task.definition.TaskAdmissionResolver taskAdmission,
            com.ifielsc.ijob.core.job.definition.JobAdmissionResolver jobAdmission) {
        this.eventBindingMapper = eventBindingMapper;
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.taskAdmission = taskAdmission;
        this.jobAdmission = jobAdmission;
    }

    public EventBindingService(EventBindingMapper eventBindingMapper) {
        this(eventBindingMapper, null, null, null, null);
    }

    public List<EventBinding> listBindingsByEventCode(String eventCode) {
        return eventBindingMapper.selectListByEventCode(eventCode);
    }

    public List<EventBindingDTO> listDetailsByTarget(TargetType targetType, Long targetId) {
        requireTarget(targetType, targetId);
        return eventBindingMapper.selectListByTarget(targetType, targetId).stream()
                .map(this::toDto)
                .toList();
    }

    public EventBindingDTO getDetail(Long id) {
        return toDto(requireBinding(id));
    }

    @Transactional
    public Long create(EventBindingCreateRequest request) {
        if (request == null
                || request.getTargetType() == null
                || request.getTargetId() == null
                || request.getTargetId() <= 0
                || request.getEventCode() == null
                || !EVENT_CODE.matcher(request.getEventCode()).matches()) {
            throw paramError("Event binding is invalid");
        }
        requireAdmittedTarget(request.getTargetType(), request.getTargetId());
        if (eventBindingMapper.selectByTargetAndEvent(
                        request.getTargetType(), request.getTargetId(), request.getEventCode())
                != null) {
            throw conflict("Event binding already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        EventBinding binding = new EventBinding();
        binding.setTargetType(request.getTargetType());
        binding.setTargetId(request.getTargetId());
        binding.setEventCode(request.getEventCode());
        binding.setEnabled(!Boolean.FALSE.equals(request.getEnabled()));
        binding.setCreateTime(now);
        binding.setUpdateTime(now);
        try {
            if (eventBindingMapper.insert(binding) != 1 || binding.getId() == null) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "Event binding creation failed");
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("Event binding already exists");
        }
        log.info(
                "event binding created, id={}, targetType={}, targetId={}, enabled={}",
                binding.getId(),
                binding.getTargetType(),
                binding.getTargetId(),
                binding.getEnabled());
        return binding.getId();
    }

    @Transactional
    public void update(Long id, EventBindingUpdateRequest request) {
        EventBinding current = requireBinding(id);
        if (request == null || request.getEnabled() == null) {
            throw paramError("Enabled is required");
        }
        if (request.getEnabled()) {
            requireAdmittedTarget(current.getTargetType(), current.getTargetId());
        }
        current.setEnabled(request.getEnabled());
        current.setUpdateTime(LocalDateTime.now());
        if (eventBindingMapper.updateEnabledById(current) != 1) {
            throw conflict("Event binding update failed");
        }
        log.info(
                "event binding enabled state updated, id={}, enabled={}", id, request.getEnabled());
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
        List<Long> existing = eventBindingMapper.selectIdsForUpdate(ids);
        if (existing == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Event binding batch lock failed");
        }
        if (!existing.equals(ids)) {
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "Some event bindings do not exist");
        }
        if (eventBindingMapper.deleteByIds(ids) != ids.size()) {
            throw conflict("Event binding deletion conflict");
        }
        log.info("event bindings deleted, count={}", ids.size());
    }

    private EventBinding requireBinding(Long id) {
        if (id == null || id <= 0) {
            throw paramError("Event binding ID is invalid");
        }
        EventBinding binding = eventBindingMapper.selectById(id);
        if (binding == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Event binding not found");
        }
        return binding;
    }

    private void requireAdmittedTarget(TargetType type, Long id) {
        if (type == TargetType.TASK) {
            taskAdmission.resolve(id);
        } else if (type == TargetType.JOB) {
            jobAdmission.resolve(id);
        } else {
            throw paramError("Event binding target is invalid");
        }
    }

    private void requireTarget(TargetType type, Long id) {
        if (type == null || id == null || id <= 0) {
            throw paramError("Event binding target is invalid");
        }
        boolean exists =
                switch (type) {
                    case TASK -> taskMapper.selectById(id) != null;
                    case JOB -> jobMapper.selectById(id) != null;
                };
        if (!exists) {
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "Event binding target not found");
        }
    }

    private EventBindingDTO toDto(EventBinding binding) {
        EventBindingDTO dto = new EventBindingDTO();
        dto.setId(binding.getId());
        dto.setTargetType(binding.getTargetType());
        dto.setTargetId(binding.getTargetId());
        dto.setEventCode(binding.getEventCode());
        dto.setEnabled(binding.getEnabled());
        dto.setCreateTime(binding.getCreateTime());
        dto.setUpdateTime(binding.getUpdateTime());
        return dto;
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    private BusinessException conflict(String message) {
        return BusinessException.localized(ResultCode.CONFLICT, message);
    }
}
