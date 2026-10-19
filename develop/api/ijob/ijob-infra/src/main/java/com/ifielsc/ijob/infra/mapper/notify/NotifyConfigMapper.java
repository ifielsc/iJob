// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.notify;

import com.ifielsc.ijob.contract.notify.NotifyConfigListDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

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
