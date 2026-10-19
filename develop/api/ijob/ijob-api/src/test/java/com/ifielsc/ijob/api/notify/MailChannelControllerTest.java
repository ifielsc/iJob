// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.notify.MailChannelRequest;
import com.ifielsc.ijob.core.notify.channel.email.MailChannelService;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.URI;

class MailChannelControllerTest {
    @Test
    void creationReturns201LocationAndExactStringId() {
        MailChannelService service =
                new MailChannelService(null, new ObjectMapper(), new AppProperties(), null) {
                    @Override
                    public Long create(MailChannelRequest request) {
                        return 9223372036854775806L;
                    }
                };
        Object result = new MailChannelController(service).create(new MailChannelRequest());
        ResponseEntity<?> response = assertInstanceOf(ResponseEntity.class, result);
        assertEquals(201, response.getStatusCode().value());
        assertEquals(
                URI.create("/api/mail-channels/9223372036854775806"),
                response.getHeaders().getLocation());
        ApiResponse<?> body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertEquals("9223372036854775806", body.getData());
    }
}
