// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.error.CoreMessage;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Explicitly retains selected header values without sending them back to the editor. */
public final class HiddenHeaders {
    private HiddenHeaders() {}

    public static Map<String, String> merge(
            Map<String, String> replacements, List<String> retained, Map<String, String> previous) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        if (retained != null) {
            if (retained.size() > 100) throw invalid();
            for (String name : retained) {
                addName(names, name);
                if (previous == null || !previous.containsKey(name)) throw invalid();
                result.put(name, previous.get(name));
            }
        }
        if (replacements != null) {
            replacements.forEach(
                    (name, value) -> {
                        addName(names, name);
                        if (value == null) throw invalid();
                        result.put(name, value);
                    });
        }
        return result;
    }

    private static void addName(Set<String> names, String name) {
        if (name == null
                || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,256}")
                || !names.add(name.toLowerCase(Locale.ROOT))) throw invalid();
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ResultCode.PARAM_ERROR,
                CoreMessage.HEADER_VALUE_IS_INVALID_OR_EXCEEDS_THE_LENGTH_LIMIT);
    }
}
