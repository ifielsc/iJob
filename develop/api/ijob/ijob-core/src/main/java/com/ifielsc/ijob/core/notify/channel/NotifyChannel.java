package com.ifielsc.ijob.core.notify.channel;

import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

public interface NotifyChannel {
    NotifyType type();

    NotifyChannelResult send(NotifyConfig config, NotifyMessage message);
}
