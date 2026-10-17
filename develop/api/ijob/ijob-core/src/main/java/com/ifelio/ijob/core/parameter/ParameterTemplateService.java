package com.ifelio.ijob.core.parameter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.contract.parameter.ParameterField;
import com.ifelio.ijob.contract.parameter.ParameterTemplateDTO;
import com.ifelio.ijob.contract.parameter.ParameterTemplateQuery;
import com.ifelio.ijob.contract.parameter.ParameterTemplateRequest;
import com.ifelio.ijob.core.management.BatchDeleteValidator;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.infra.entity.parameter.ParameterTemplate;
import com.ifelio.ijob.infra.mapper.parameter.ParameterTemplateMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParameterTemplateService {
    private final ParameterTemplateMapper mapper;
    private final ObjectMapper json;

    public PageResult<ParameterTemplateDTO> queryPage(ParameterTemplateQuery query) {
        ParameterTemplateQuery q = query == null ? new ParameterTemplateQuery() : query;
        PageHelper.startPage(q.getPageNo(), q.getPageSize());
        try {
            var page = PageResults.from(mapper.selectList(q));
            PageResult<ParameterTemplateDTO> result = new PageResult<>();
            BeanUtils.copyProperties(page, result, "list");
            result.setList(page.getList().stream().map(this::view).toList());
            return result;
        } finally {
            PageHelper.clearPage();
        }
    }

    public ParameterTemplateDTO getDetail(Long id) {
        return view(require(id));
    }

    @Transactional
    public Long create(ParameterTemplateRequest request) {
        ParameterTemplate row = prepare(request);
        row.setRevision(1L);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, ParameterMessage.DUPLICATE);
        }
        return row.getId();
    }

    @Transactional
    public void update(Long id, ParameterTemplateRequest request) {
        require(id);
        ParameterTemplate row = prepare(request);
        row.setId(id);
        try {
            if (request.getRevision() == null || mapper.updateById(row) != 1)
                throw new BusinessException(ResultCode.CONFLICT, ParameterMessage.CHANGED);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, ParameterMessage.DUPLICATE);
        }
    }

    @Transactional
    public void delete(Long id) {
        deleteIds(BatchDeleteValidator.validate(BatchDeleteValidator.single(id)));
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        deleteIds(BatchDeleteValidator.validate(request));
    }

    public ParameterTemplateDTO getBinding(String type, Long targetId) {
        target(type, targetId);
        Long id = mapper.selectBinding(type, targetId);
        return id == null ? null : getDetail(id);
    }

    @Transactional
    public void bind(String type, Long targetId, Long templateId) {
        target(type, targetId);
        if (templateId != null && mapper.selectByIdForUpdate(templateId) == null)
            throw new BusinessException(ResultCode.NOT_FOUND, ParameterMessage.NOT_FOUND);
        mapper.deleteBinding(type, targetId);
        if (templateId != null) mapper.insertBinding(type, targetId, templateId);
    }

    private void deleteIds(List<Long> ids) {
        // Lock in stable order; bindings use the same lock before inserting a reference.
        for (Long id : ids) {
            if (mapper.selectByIdForUpdate(id) == null)
                throw new BusinessException(ResultCode.NOT_FOUND, ParameterMessage.NOT_FOUND);
        }
        if (mapper.deleteByIds(ids) != ids.size())
            throw new BusinessException(ResultCode.CONFLICT, ParameterMessage.CHANGED);
    }

    private void target(String type, Long id) {
        if (!("TASK".equals(type) || "JOB".equals(type)) || id == null || id <= 0)
            throw ParameterRules.invalid("target");
        if (mapper.countTarget(type, id) != 1)
            throw new BusinessException(ResultCode.NOT_FOUND, ParameterMessage.NOT_FOUND);
    }

    private ParameterTemplate require(Long id) {
        ParameterTemplate row = id == null ? null : mapper.selectById(id);
        if (row == null)
            throw new BusinessException(ResultCode.NOT_FOUND, ParameterMessage.NOT_FOUND);
        return row;
    }

    private ParameterTemplate prepare(ParameterTemplateRequest r) {
        if (r == null
                || r.getName() == null
                || r.getName().isBlank()
                || r.getName().trim().length() > 128
                || r.getDescription() != null && r.getDescription().length() > 500
                || r.getFields() == null) throw ParameterRules.invalid("template");
        ParameterRules.definitions(r.getFields());
        ParameterTemplate row = new ParameterTemplate();
        row.setName(r.getName().trim());
        row.setDescription(r.getDescription());
        row.setRevision(r.getRevision());
        try {
            String fields = json.writeValueAsString(r.getFields());
            if (fields.getBytes(StandardCharsets.UTF_8).length > 65536)
                throw ParameterRules.invalid("fields");
            row.setFieldsJson(fields);
        } catch (JsonProcessingException e) {
            throw ParameterRules.invalid("fields");
        }
        return row;
    }

    private ParameterTemplateDTO view(ParameterTemplate row) {
        ParameterTemplateDTO dto = new ParameterTemplateDTO();
        BeanUtils.copyProperties(row, dto);
        try {
            dto.setFields(
                    json.readerFor(new TypeReference<List<ParameterField>>() {})
                            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                            .readValue(row.getFieldsJson()));
        } catch (JsonProcessingException e) {
            throw ParameterRules.invalid("stored fields");
        }
        return dto;
    }
}
