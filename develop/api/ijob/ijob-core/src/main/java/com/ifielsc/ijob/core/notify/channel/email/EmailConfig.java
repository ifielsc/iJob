// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.email;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString(exclude = {"from", "to", "cc"})
public class EmailConfig {
    private Long mailChannelId;
    private String from;
    private List<String> to;
    private List<String> cc;
}
