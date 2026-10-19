package com.ifielsc.ijob.core.resource;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceDTO;
import com.ifielsc.ijob.contract.resource.ResourceListDTO;
import com.ifielsc.ijob.contract.resource.ResourcePageQuery;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationValidationService;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.resource.ResourceMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionResourceReferenceMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceService {
    private static final int MAX_CODE_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final ResourceMapper resourceMapper;
    private final ResourceConfigService configService;
    private final TaskDefinitionResourceReferenceMapper referenceMapper;
    private final TaskPluginConfigPublicationValidationService pluginConfigValidationService;
    private final ApplicationEventPublisher eventPublisher;

    public PageResult<ResourceListDTO> queryPage(ResourcePageQuery query) {
        ResourcePageQuery resolvedQuery = query == null ? new ResourcePageQuery() : query;
        PageHelper.startPage(resolvedQuery.getPageNo(), resolvedQuery.getPageSize());
        try {
            return PageResults.from(resourceMapper.selectDtoList(resolvedQuery));
        } finally {
            PageHelper.clearPage();
        }
    }

    public ResourceDTO getDetail(Long id) {
        Resource resource = requireResource(id);
        ResourceDTO dto = new ResourceDTO();
        BeanUtils.copyProperties(resource, dto);
        dto.setConfig(configService.view(resource));
        return dto;
    }

    public Resource getResource(Long id) {
        return resourceMapper.selectById(id);
    }

    @Transactional
    public Long create(ResourceUpsertRequest request) {
        validateRequest(request);
        String code = request.getCode().trim();
        ensureCodeAvailable(code, null);

        LocalDateTime now = LocalDateTime.now();
        Resource resource = toResource(request);
        resource.setCode(code);
        resource.setConfig(configService.protect(request, null));
        resource.setCreateTime(now);
        resource.setUpdateTime(now);
        if (resourceMapper.insert(resource) != 1 || resource.getId() == null) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, CoreMessage.RESOURCE_CREATION_FAILED);
        }

        log.info("resource created, id={}, type={}", resource.getId(), resource.getType());
        return resource.getId();
    }

    @Transactional
    public void update(Long resourceId, ResourceUpsertRequest request) {
        validateRequest(request);
        // Merge hidden fields only after serializing updates against the persisted resource.
        if (request.getType() == ResourceType.KAFKA
                || request.getType() == ResourceType.RABBITMQ
                || request.getDbConfig() != null
                        && Boolean.TRUE.equals(request.getDbConfig().getPreserveExisting())
                || request.getServiceConfig() != null
                        && (Boolean.TRUE.equals(request.getServiceConfig().getPreserveExisting())
                                || request.getServiceConfig().getPreservedHeaderNames() != null)
                || request.getSshConfig() != null
                        && Boolean.TRUE.equals(request.getSshConfig().getPreserveExisting())) {
            resourceMapper.selectIdsForUpdate(List.of(resourceId));
        }
        Resource existing = requireResource(resourceId);
        if ((existing.getType() == ResourceType.KAFKA
                        || existing.getType() == ResourceType.RABBITMQ)
                && request.getType() != existing.getType())
            throw paramError(
                    CoreMessage.EXACTLY_ONE_CONFIGURATION_MATCHING_THE_RESOURCE_TYPE_IS_REQUIRED);
        String code = request.getCode().trim();
        ensureCodeAvailable(code, resourceId);

        Resource resource = toResource(request);
        resource.setId(resourceId);
        resource.setCode(code);
        resource.setConfig(configService.protect(request, existing));
        resource.setUpdateTime(LocalDateTime.now());
        if (resourceMapper.updateById(resource) != 1) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.RESOURCE_UPDATE_FAILED);
        }
        eventPublisher.publishEvent(new ResourceCacheInvalidationEvent(resourceId));

        log.info("resource updated, id={}, type={}", resourceId, resource.getType());
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
        List<Long> existing = resourceMapper.selectIdsForUpdate(ids);
        if (existing == null) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR, CoreMessage.RESOURCE_BATCH_LOCKING_FAILED);
        }
        if (!existing.equals(ids)) {
            throw new BusinessException(
                    ResultCode.NOT_FOUND, CoreMessage.SOME_RESOURCES_DO_NOT_EXIST);
        }
        requireNoDefinitionReferences(ids);
        if (resourceMapper.deleteByIds(ids) != ids.size()) {
            throw new BusinessException(
                    ResultCode.CONFLICT,
                    CoreMessage.RESOURCE_DELETION_CONFLICTS_WITH_A_CONCURRENT_UPDATE);
        }
        ids.forEach(id -> eventPublisher.publishEvent(new ResourceCacheInvalidationEvent(id)));
        log.info("resources deleted, count={}", ids.size());
    }

    private void requireNoDefinitionReferences(List<Long> ids) {
        Set<Long> deleting = Set.copyOf(ids);
        long started = System.nanoTime();
        long scanned = 0;
        String outcome = "failed";
        try {
            // Publishers acquire the same resource row locks before writing definitions. Keep
            // those locks until deletion commits; paging must not open a publish/delete gap.
            Long upperId = referenceMapper.selectMaxDefinitionId();
            long afterId = 0;
            if (upperId != null && upperId <= 0) {
                throw new BusinessException(
                        ResultCode.SYSTEM_ERROR, CoreMessage.RESOURCE_REFERENCE_CHECK_FAILED);
            }
            while (upperId != null && afterId < upperId) {
                List<TaskDefinition> definitions;
                PageHelper.startPage(1, 500, false);
                try {
                    definitions = referenceMapper.selectDefinitionBatch(afterId, upperId);
                } finally {
                    PageHelper.clearPage();
                }
                if (definitions == null || definitions.size() > 500) {
                    throw new BusinessException(
                            ResultCode.SYSTEM_ERROR, CoreMessage.RESOURCE_REFERENCE_CHECK_FAILED);
                }
                if (definitions.isEmpty()) break;
                for (TaskDefinition definition : definitions) {
                    if (definition == null
                            || definition.getId() == null
                            || definition.getId() <= afterId
                            || definition.getId() > upperId
                            || definition.getPluginType() == null
                            || definition.getPluginConfig() == null) {
                        throw new BusinessException(
                                ResultCode.SYSTEM_ERROR,
                                CoreMessage.TASK_DEFINITION_RESOURCE_REFERENCES_DATA_IS_INCOMPLETE);
                    }
                    afterId = definition.getId();
                    scanned++;
                    Set<Long> referenced =
                            pluginConfigValidationService.referencedResourceIds(
                                    definition.getPluginType(), definition.getPluginConfig());
                    if (referenced.stream().anyMatch(deleting::contains)) {
                        outcome = "referenced";
                        throw new BusinessException(
                                ResultCode.CONFLICT,
                                CoreMessage.RESOURCE_IS_STILL_REFERENCED_BY_TASK_DEFINITIONS);
                    }
                }
            }
            outcome = "clear";
        } finally {
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (millis >= 1000 || !"clear".equals(outcome)) {
                log.warn(
                        "Resource reference scan, resources={}, definitions={}, elapsedMs={},"
                                + " outcome={}",
                        ids.size(),
                        scanned,
                        millis,
                        outcome);
            } else {
                log.debug(
                        "Resource reference scan, resources={}, definitions={}, elapsedMs={},"
                                + " outcome={}",
                        ids.size(),
                        scanned,
                        millis,
                        outcome);
            }
        }
    }

    private Resource requireResource(Long id) {
        requirePositiveId(id);
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.RESOURCE_DOES_NOT_EXIST);
        }
        return resource;
    }

    private void ensureCodeAvailable(String code, Long currentId) {
        Resource duplicate = resourceMapper.selectByCode(code);
        if (duplicate != null && !duplicate.getId().equals(currentId)) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.RESOURCE_CODE_ALREADY_EXISTS);
        }
    }

    private void validateRequest(ResourceUpsertRequest request) {
        if (request == null) {
            throw paramError(CoreMessage.REQUEST_BODY_IS_REQUIRED);
        }
        if (isBlank(request.getCode())) {
            throw paramError(CoreMessage.RESOURCE_CODE_IS_REQUIRED);
        }
        String code = request.getCode().trim();
        if (code.length() > MAX_CODE_LENGTH || !CODE_PATTERN.matcher(code).matches()) {
            throw paramError(
                    CoreMessage
                            .RESOURCE_CODE_MUST_CONTAIN_ONLY_LETTERS_DIGITS_DOTS_UNDERSCORES_OR_HYPHENS_AND_AT_MOST_128_CHARACTERS);
        }
        if (isBlank(request.getName())) {
            throw paramError(CoreMessage.RESOURCE_NAME_IS_REQUIRED);
        }
        if (request.getName().trim().length() > MAX_NAME_LENGTH) {
            throw paramError(CoreMessage.RESOURCE_NAME_MUST_NOT_EXCEED_128_CHARACTERS);
        }
        if (request.getType() == null) {
            throw paramError(CoreMessage.RESOURCE_TYPE_IS_REQUIRED);
        }
        if (request.getDescription() != null
                && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw paramError(CoreMessage.RESOURCE_DESCRIPTION_MUST_NOT_EXCEED_500_CHARACTERS);
        }
    }

    private Resource toResource(ResourceUpsertRequest request) {
        Resource resource = new Resource();
        resource.setName(request.getName().trim());
        resource.setType(request.getType());
        resource.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        resource.setDescription(request.getDescription());
        return resource;
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw paramError(CoreMessage.RESOURCE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException paramError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
