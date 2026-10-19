// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.notify;

import com.ifielsc.ijob.contract.notify.MailChannelPageQuery;
import com.ifielsc.ijob.infra.entity.notify.MailChannel;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MailChannelMapper {
    List<MailChannel> selectList(MailChannelPageQuery query);

    MailChannel selectById(@Param("id") Long id);

    MailChannel selectByIdForUpdate(@Param("id") Long id);

    int insert(MailChannel channel);

    int updateById(MailChannel channel);

    List<String> selectEmailConfigurations();

    List<String> selectPendingEmailSnapshots();

    int deleteByIds(@Param("ids") List<Long> ids);
}
