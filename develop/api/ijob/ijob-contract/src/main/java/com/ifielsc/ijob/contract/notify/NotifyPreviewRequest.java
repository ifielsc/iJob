// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;

import java.util.List;

/** Presentation options only: previews never accept credentials or a delivery destination. */
public record NotifyPreviewRequest(
        NotifyType type,
        String source,
        String format,
        List<String> members,
        List<String> mobiles,
        List<String> roles,
        boolean atAll,
        String mentionMode,
        String keyword) {}
