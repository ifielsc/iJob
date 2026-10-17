package com.ifelio.ijob.core.notify.template;

import com.ifelio.ijob.core.notify.NotificationContext;
import com.ifelio.ijob.core.notify.NotifyMessage;

public interface NotifyTemplate {
    NotifyMessage build(NotificationContext context);
}
