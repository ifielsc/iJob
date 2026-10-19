// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.notify;

import com.ifielsc.ijob.contract.notify.NotifySendLogDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.infra.entity.notify.NotifySendLog;

import java.util.List;

public interface NotifySendLogMapper {
    int insertStarted(NotifySendLog log);

    int completeIfStarted(NotifySendLog log);

    List<NotifySendLogDTO> selectDtoList(NotifySendLogPageQuery query);
}
