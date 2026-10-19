// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.parameter;

import com.ifielsc.ijob.contract.parameter.ParameterTemplateQuery;
import com.ifielsc.ijob.infra.entity.parameter.ParameterTemplate;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ParameterTemplateMapper {
    List<ParameterTemplate> selectList(ParameterTemplateQuery query);

    ParameterTemplate selectById(@Param("id") Long id);

    int insert(ParameterTemplate row);

    int updateById(ParameterTemplate row);

    ParameterTemplate selectByIdForUpdate(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);

    Long selectBinding(@Param("type") String type, @Param("targetId") Long targetId);

    int deleteBinding(@Param("type") String type, @Param("targetId") Long targetId);

    int insertBinding(
            @Param("type") String type,
            @Param("targetId") Long targetId,
            @Param("templateId") Long templateId);

    int countTarget(@Param("type") String type, @Param("targetId") Long targetId);
}
