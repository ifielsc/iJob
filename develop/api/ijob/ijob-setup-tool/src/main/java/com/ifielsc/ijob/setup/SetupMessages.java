// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import java.util.Locale;
import java.util.ResourceBundle;

final class SetupMessages {
    private SetupMessages() {}

    static String text(boolean chinese, String key) {
        return ResourceBundle.getBundle(
                        "setup-messages", chinese ? Locale.SIMPLIFIED_CHINESE : Locale.ROOT)
                .getString(key);
    }
}
