package com.ifelio.ijob.infra.coordination.notification;

public enum NotificationOutboxStatus {
    READY,
    CLAIMED,
    RETRY_WAIT,
    DELIVERED,
    DEAD
}
