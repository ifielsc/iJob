package com.ifelio.ijob.infra.mapper.notify;

import com.ifelio.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationDeliveryMapper {
    List<NotificationDeliveryDTO> selectDtoList(NotifySendLogPageQuery query);

    NotificationDeliveryDTO selectDtoById(@Param("id") Long id);
}
