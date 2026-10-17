package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifyType;

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
