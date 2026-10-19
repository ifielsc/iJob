package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;

public interface NotifyTemplate {
    NotifyMessage build(NotificationContext context);
}
