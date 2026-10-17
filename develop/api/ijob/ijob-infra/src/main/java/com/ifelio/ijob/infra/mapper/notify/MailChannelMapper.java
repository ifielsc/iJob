package com.ifelio.ijob.infra.mapper.notify;

import com.ifelio.ijob.contract.notify.MailChannelPageQuery;
import com.ifelio.ijob.infra.entity.notify.MailChannel;

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
