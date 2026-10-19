// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.runtime;

@FunctionalInterface
public interface RuntimeFenceListener {
    void onRuntimeFenced(String reason);
}
