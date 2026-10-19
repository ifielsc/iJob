// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MailChannelDTO {
    private Long id;
    private String name;
    private Boolean enabled;
    private Long revision;
    private String host;
    private Integer port;
    private String security;
    private Boolean authentication;
    private String username;
    private boolean passwordConfigured;
    private String from;
    private LocalDateTime updateTime;
}
