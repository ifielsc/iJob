package com.ifelio.ijob.infra.mapper.notify;

import com.ifelio.ijob.contract.notify.NotifyConfigListDTO;
import com.ifelio.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotifyConfigMapper {
    List<NotifyConfigListDTO> selectSummariesByIds(@Param("ids") List<Long> ids);

    List<NotifyConfigListDTO> selectDtoList(NotifyConfigPageQuery query);

    NotifyConfig selectById(@Param("id") Long id);

    List<Long> selectIdsForUpdate(@Param("ids") List<Long> ids);

    int countById(@Param("id") Long id);

    int insert(NotifyConfig config);

    int updateById(NotifyConfig config);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);
}
