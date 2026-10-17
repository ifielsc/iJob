package com.ifelio.ijob.core.notify.channel;

import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;

public interface NotifyChannel {
    NotifyType type();

    NotifyChannelResult send(NotifyConfig config, NotifyMessage message);
}
