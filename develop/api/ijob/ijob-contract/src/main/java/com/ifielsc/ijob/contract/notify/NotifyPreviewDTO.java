// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;

import java.util.Map;

/** The actual channel payload rendered from sample data, without transport credentials. */
public record NotifyPreviewDTO(
        NotifyType type, String source, String subject, Map<String, Object> payload) {}
