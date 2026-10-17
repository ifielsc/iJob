package com.ifelio.ijob.infra.mapper.resource;

import com.ifelio.ijob.contract.resource.ResourceListDTO;
import com.ifelio.ijob.contract.resource.ResourcePageQuery;
import com.ifelio.ijob.infra.entity.resource.Resource;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ResourceMapper {
    List<ResourceListDTO> selectSummariesByIds(@Param("ids") List<Long> ids);

    List<ResourceListDTO> selectDtoList(ResourcePageQuery query);

    Resource selectById(@Param("id") Long id);

    List<Long> selectIdsForUpdate(@Param("ids") List<Long> ids);

    Resource selectByCode(@Param("code") String code);

    int insert(Resource resource);

    int updateById(Resource resource);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);
}
