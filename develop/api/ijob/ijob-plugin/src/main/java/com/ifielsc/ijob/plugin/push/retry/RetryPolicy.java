// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.retry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetryPolicy {
    /** Maximum retries after the initial attempt; zero disables retries. */
    @Builder.Default private int maxRetries = 3;

    /** Initial retry delay in milliseconds. */
    @Builder.Default private long initialInterval = 1000;

    @Builder.Default private boolean exponential = true;
}
