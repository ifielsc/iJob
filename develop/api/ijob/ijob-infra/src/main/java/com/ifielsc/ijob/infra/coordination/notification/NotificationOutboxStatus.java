// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.notification;

public enum NotificationOutboxStatus {
    READY,
    CLAIMED,
    RETRY_WAIT,
    DELIVERED,
    DEAD
}
