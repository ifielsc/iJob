// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.NotifyPolicy;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifyBindingCreateRequest;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.notify.NotifyBindingUpdateRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigListDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifielsc.ijob.contract.task.TaskListDTO;
import com.ifielsc.ijob.contract.task.TaskPageQuery;
import com.ifielsc.ijob.infra.entity.job.Job;
import com.ifielsc.ijob.infra.entity.notify.NotifyBinding;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.entity.task.Task;
import com.ifielsc.ijob.infra.mapper.job.JobMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyBindingMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyConfigMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskMapper;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

class NotifyBindingServiceTest {
    @Test
    void createShouldValidateReferencesAndPersistImmutableIdentity() {
        RecordingBindingMapper bindings = new RecordingBindingMapper();
        RecordingConfigMapper configs = new RecordingConfigMapper();
        NotifyBindingService service = service(bindings, configs, true, false);

        Long id = service.create(request(TargetType.TASK, 7L));

        assertEquals(301L, id);
        assertEquals(TargetType.TASK, bindings.binding.getTargetType());
        assertEquals(7L, bindings.binding.getTargetId());
        assertEquals(9L, bindings.binding.getNotifyConfigId());
        assertEquals(NotifyPolicy.ON_FAILURE, bindings.binding.getNotifyPolicy());
        assertNotNull(bindings.binding.getCreateTime());
        assertNotNull(bindings.binding.getUpdateTime());
    }

    @Test
    void createShouldRejectMissingTargetConfigAndDuplicate() {
        RecordingBindingMapper bindings = new RecordingBindingMapper();
        RecordingConfigMapper configs = new RecordingConfigMapper();
        BusinessException missingTarget =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service(bindings, configs, false, false)
                                        .create(request(TargetType.TASK, 7L)));

        configs.exists = false;
        BusinessException missingConfig =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service(bindings, configs, true, false)
                                        .create(request(TargetType.TASK, 7L)));

        configs.exists = true;
        bindings.duplicate = true;
        BusinessException duplicate =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service(bindings, configs, true, false)
                                        .create(request(TargetType.TASK, 7L)));

        assertEquals(ResultCode.NOT_FOUND, missingTarget.getCode());
        assertEquals(ResultCode.NOT_FOUND, missingConfig.getCode());
        assertEquals(ResultCode.CONFLICT, duplicate.getCode());
    }

    @Test
    void concurrentDuplicateShouldTranslateToConflict() {
        RecordingBindingMapper bindings = new RecordingBindingMapper();
        bindings.failInsertWithDuplicate = true;
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service(bindings, new RecordingConfigMapper(), true, false)
                                        .create(request(TargetType.TASK, 7L)));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
    }

    @Test
    void listDetailAndUpdateShouldUseContractDtoAndPolicyOnly() {
        RecordingBindingMapper bindings = new RecordingBindingMapper();
        bindings.binding = storedBinding(8L);
        NotifyBindingDTO dto = new NotifyBindingDTO();
        dto.setId(8L);
        dto.setNotifyConfigName("operations");
        bindings.dto = dto;
        NotifyBindingService service = service(bindings, new RecordingConfigMapper(), true, false);

        List<NotifyBindingDTO> listed = service.listDetailsByTarget(TargetType.TASK, 7L);
        NotifyBindingDTO detail = service.getDetail(8L);
        NotifyBindingUpdateRequest update = new NotifyBindingUpdateRequest();
        update.setNotifyPolicy(NotifyPolicy.ALWAYS);
        service.update(8L, update);

        assertEquals(List.of(dto), listed);
        assertEquals(dto, detail);
        assertEquals(8L, bindings.policyUpdate.getId());
        assertEquals(NotifyPolicy.ALWAYS, bindings.policyUpdate.getNotifyPolicy());
        assertNull(bindings.policyUpdate.getTargetType());
        assertNull(bindings.policyUpdate.getNotifyConfigId());
    }

    @Test
    void deleteAndTargetCleanupShouldBeIdempotentAtAggregateBoundary() {
        RecordingBindingMapper bindings = new RecordingBindingMapper();
        bindings.binding = storedBinding(8L);
        NotifyBindingService service = service(bindings, new RecordingConfigMapper(), true, false);

        service.delete(8L);
        service.deleteBindingsByTarget(TargetType.TASK, 7L);
        service.deleteBindingsByTargets(TargetType.TASK, List.of(7L, 7L, 9L));
        service.deleteBindingsByTargets(TargetType.TASK, List.of());

        assertTrue(bindings.deleteByIdCalled);
        assertTrue(bindings.deleteByTargetCalled);
        assertEquals(List.of(7L, 9L), bindings.deletedTargetIds);
        assertFalse(bindings.exists);
    }

    private NotifyBindingService service(
            RecordingBindingMapper bindings,
            RecordingConfigMapper configs,
            boolean taskExists,
            boolean jobExists) {
        return new NotifyBindingService(
                bindings,
                configs,
                new RecordingTaskMapper(taskExists),
                new RecordingJobMapper(jobExists));
    }

    private NotifyBindingCreateRequest request(TargetType targetType, Long targetId) {
        NotifyBindingCreateRequest request = new NotifyBindingCreateRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setNotifyConfigId(9L);
        request.setNotifyPolicy(NotifyPolicy.ON_FAILURE);
        return request;
    }

    private NotifyBinding storedBinding(Long id) {
        NotifyBinding binding = new NotifyBinding();
        binding.setId(id);
        binding.setTargetType(TargetType.TASK);
        binding.setTargetId(7L);
        binding.setNotifyConfigId(9L);
        binding.setNotifyPolicy(NotifyPolicy.ON_FAILURE);
        return binding;
    }

    private static final class RecordingBindingMapper implements NotifyBindingMapper {
        private NotifyBinding binding;
        private NotifyBindingDTO dto;
        private NotifyBinding policyUpdate;
        private boolean exists = true;
        private boolean duplicate;
        private boolean failInsertWithDuplicate;
        private boolean deleteByIdCalled;
        private boolean deleteByTargetCalled;
        private List<Long> deletedTargetIds = List.of();

        @Override
        public List<NotifyBindingDTO> selectDtoListByTarget(TargetType targetType, Long targetId) {
            return dto == null ? List.of() : List.of(dto);
        }

        @Override
        public NotifyBindingDTO selectDtoById(Long id) {
            return dto != null && id.equals(dto.getId()) ? dto : null;
        }

        @Override
        public NotifyBinding selectById(Long id) {
            return exists && binding != null && id.equals(binding.getId()) ? binding : null;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return exists && binding != null && ids.contains(binding.getId())
                    ? List.of(binding.getId())
                    : List.of();
        }

        @Override
        public NotifyBinding selectByTargetAndConfig(
                TargetType targetType, Long targetId, Long notifyConfigId) {
            return duplicate ? storedDuplicate(targetType, targetId, notifyConfigId) : null;
        }

        @Override
        public List<NotifyBinding> selectListByTargetId(TargetType targetType, Long targetId) {
            return binding == null ? List.of() : List.of(binding);
        }

        @Override
        public int insert(NotifyBinding inserted) {
            if (failInsertWithDuplicate) {
                throw new DuplicateKeyException("duplicate");
            }
            inserted.setId(301L);
            binding = inserted;
            exists = true;
            return 1;
        }

        @Override
        public int updatePolicyById(NotifyBinding updated) {
            policyUpdate = updated;
            return 1;
        }

        @Override
        public int deleteById(Long id) {
            deleteByIdCalled = true;
            exists = false;
            return 1;
        }

        @Override
        public int deleteByIds(List<Long> ids) {
            deleteByIdCalled = true;
            exists = false;
            return ids.size();
        }

        @Override
        public int countByNotifyConfigIds(List<Long> notifyConfigIds) {
            return 0;
        }

        @Override
        public int deleteByTargetId(TargetType targetType, Long targetId) {
            deleteByTargetCalled = true;
            return 1;
        }

        @Override
        public int deleteByTargetIds(TargetType targetType, List<Long> targetIds) {
            deletedTargetIds = targetIds;
            return targetIds.size();
        }

        private NotifyBinding storedDuplicate(
                TargetType targetType, Long targetId, Long notifyConfigId) {
            NotifyBinding duplicateBinding = new NotifyBinding();
            duplicateBinding.setId(99L);
            duplicateBinding.setTargetType(targetType);
            duplicateBinding.setTargetId(targetId);
            duplicateBinding.setNotifyConfigId(notifyConfigId);
            return duplicateBinding;
        }
    }

    private static final class RecordingConfigMapper implements NotifyConfigMapper {
        @Override
        public java.util.List<com.ifielsc.ijob.contract.notify.NotifyConfigListDTO>
                selectSummariesByIds(java.util.List<Long> ids) {
            throw new UnsupportedOperationException("Unexpected summary lookup");
        }

        private boolean exists = true;

        @Override
        public List<NotifyConfigListDTO> selectDtoList(NotifyConfigPageQuery query) {
            return List.of();
        }

        @Override
        public NotifyConfig selectById(Long id) {
            return null;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return List.of();
        }

        @Override
        public int countById(Long id) {
            return exists ? 1 : 0;
        }

        @Override
        public int insert(NotifyConfig config) {
            return 0;
        }

        @Override
        public int updateById(NotifyConfig config) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            return 0;
        }

        @Override
        public int deleteByIds(List<Long> ids) {
            return 0;
        }
    }

    private static final class RecordingTaskMapper implements TaskMapper {
        @Override
        public List<String> selectGroupCodes(String pattern) {
            return List.of();
        }

        private final boolean exists;

        private RecordingTaskMapper(boolean exists) {
            this.exists = exists;
        }

        @Override
        public List<TaskListDTO> selectDtoList(TaskPageQuery query) {
            return List.of();
        }

        @Override
        public Task selectById(Long id) {
            return exists ? new Task() : null;
        }
    }

    private static final class RecordingJobMapper implements JobMapper {
        private final boolean exists;

        private RecordingJobMapper(boolean exists) {
            this.exists = exists;
        }

        @Override
        public Job selectById(Long id) {
            return exists ? new Job() : null;
        }
    }
}
