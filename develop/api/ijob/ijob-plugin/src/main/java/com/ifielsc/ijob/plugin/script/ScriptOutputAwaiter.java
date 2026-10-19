// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ScriptOutputAwaiter {
    private ScriptOutputAwaiter() {}

    public static String await(Future<String> future, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, ScriptOutputCollectionException {
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException exception) {
            throw new ScriptOutputCollectionException("Script output collection did not finish");
        }
    }
}
