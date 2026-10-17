package com.ifelio.ijob.core.notify.channel.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.contract.notify.MailChannelDTO;

import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Base64;

public final class MailChannelTestSupport {
    private MailChannelTestSupport() {}

    public static MailChannelService channel(String from) {
        return channel(from, new JavaMailSenderImpl());
    }

    public static MailChannelService channel(String from, JavaMailSenderImpl sender) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getAes().setKeyId("mail-test");
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new MailChannelService(null, new ObjectMapper(), properties, null) {
            @Override
            public MailChannelDTO getEnabledDetail(Long id) {
                return getDetail(id);
            }

            @Override
            public MailChannelDTO getDetail(Long id) {
                MailChannelDTO dto = new MailChannelDTO();
                dto.setId(id);
                dto.setName("Test mail");
                dto.setFrom(from);
                dto.setEnabled(true);
                return dto;
            }

            @Override
            public Delivery delivery(Long id) {
                return new Delivery(sender, from);
            }
        };
    }
}
