package com.ifielsc.ijob.infra.coordination.notification;

public enum NotificationOutboxStatus {
    READY,
    CLAIMED,
    RETRY_WAIT,
    DELIVERED,
    DEAD
}
