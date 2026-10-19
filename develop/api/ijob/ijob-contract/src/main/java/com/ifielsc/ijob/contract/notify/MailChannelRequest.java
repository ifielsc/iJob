// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailChannelRequest {
    private String name;
    private Boolean enabled;
    private Long revision;
    private String host;
    private Integer port;
    private String security;
    private Boolean authentication;
    private String username;
    private String password;
    private String from;
}
