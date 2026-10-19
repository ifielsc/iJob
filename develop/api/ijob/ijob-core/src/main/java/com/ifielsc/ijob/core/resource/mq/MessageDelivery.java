// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.mq;

import java.util.Map;

/** Opaque receipt belongs to this session only and never becomes an ETL business field. */
public record MessageDelivery(
        byte[] body, String encoding, Map<String, Object> metadata, Object receipt) {}
