// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.work;

public enum TriggerWorkStatus {
    READY,
    CLAIMED,
    SUSPENDED,
    RETRY_WAIT,
    COMPLETED,
    DEAD
}
