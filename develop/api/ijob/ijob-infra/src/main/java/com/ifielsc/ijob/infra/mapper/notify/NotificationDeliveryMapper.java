// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.notify;

import com.ifielsc.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationDeliveryMapper {
    List<NotificationDeliveryDTO> selectDtoList(NotifySendLogPageQuery query);

    NotificationDeliveryDTO selectDtoById(@Param("id") Long id);
}
