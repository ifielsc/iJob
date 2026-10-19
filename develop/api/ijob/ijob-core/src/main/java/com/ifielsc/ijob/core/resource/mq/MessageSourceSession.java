// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.mq;

import java.time.Duration;
import java.util.List;

public interface MessageSourceSession extends AutoCloseable {
    MessageDelivery poll(Duration timeout);

    /** Called only after every delivery in this window has durably reached the target. */
    void confirm(List<MessageDelivery> deliveries);

    @Override
    void close();
}
