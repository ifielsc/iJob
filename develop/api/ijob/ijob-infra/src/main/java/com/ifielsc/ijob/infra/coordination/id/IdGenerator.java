// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.id;

@FunctionalInterface
public interface IdGenerator {
    long nextId(IdNamespace namespace);
}
