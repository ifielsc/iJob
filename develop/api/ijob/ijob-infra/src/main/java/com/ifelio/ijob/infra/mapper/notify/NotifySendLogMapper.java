package com.ifelio.ijob.infra.mapper.notify;

import com.ifelio.ijob.contract.notify.NotifySendLogDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifelio.ijob.infra.entity.notify.NotifySendLog;

import java.util.List;

public interface NotifySendLogMapper {
    int insertStarted(NotifySendLog log);

    int completeIfStarted(NotifySendLog log);

    List<NotifySendLogDTO> selectDtoList(NotifySendLogPageQuery query);
}
