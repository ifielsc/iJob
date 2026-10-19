// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.publisher;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractPushPublisher implements PushPublisher {
    @Getter protected final PushPublisher delegate;

    @Override
    public void flush() {
        if (delegate != null) {
            delegate.flush();
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }
}
