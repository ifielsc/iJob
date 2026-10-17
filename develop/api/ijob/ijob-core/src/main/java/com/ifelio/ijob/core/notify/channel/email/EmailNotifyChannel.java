package com.ifelio.ijob.core.notify.channel.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifelio.ijob.core.notify.NotifyAuditFailure;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.core.notify.channel.NotifyChannel;
import com.ifelio.ijob.core.notify.channel.NotifyChannelResult;
import com.ifelio.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;

import jakarta.mail.internet.MimeMessage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        prefix = "app.notify.email",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailNotifyChannel implements NotifyChannel {
    private final ObjectMapper objectMapper;
    private final MailChannelService mailChannels;

    public EmailNotifyChannel(MailChannelService mailChannels, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.mailChannels = mailChannels;
    }

    @Override
    public NotifyType type() {
        return NotifyType.EMAIL;
    }

    @Override
    public NotifyChannelResult send(NotifyConfig config, NotifyMessage message) {
        MimeMessage mimeMessage;
        JavaMailSender selectedSender;
        try {
            message = message.forChannel(NotifyType.EMAIL);
            EmailConfig emailConfig = objectMapper.readValue(config.getConfig(), EmailConfig.class);
            if (emailConfig.getMailChannelId() == null)
                throw new IllegalStateException("Select a mail channel before sending");
            MailChannelService.Delivery delivery =
                    mailChannels.delivery(emailConfig.getMailChannelId());
            selectedSender = delivery.sender();
            String from = delivery.from();
            mimeMessage = selectedSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(emailConfig.getTo().toArray(String[]::new));
            if (emailConfig.getCc() != null && !emailConfig.getCc().isEmpty()) {
                helper.setCc(emailConfig.getCc().toArray(String[]::new));
            }
            helper.setSubject(message.getSubject());
            helper.setText(message.getContent(), true);

        } catch (Exception ex) {
            throw new NotifyDeliveryException(NotifyAuditFailure.CONFIG_INVALID, ex);
        }
        try {
            selectedSender.send(mimeMessage);
            return NotifyChannelResult.DELIVERED;
        } catch (org.springframework.mail.MailAuthenticationException ex) {
            throw new NotifyDeliveryException(NotifyAuditFailure.AUTHENTICATION_FAILED, ex);
        } catch (org.springframework.mail.MailException ex) {
            throw new NotifyDeliveryException(
                    TimeoutExceptionClassifier.isTimeout(ex)
                            ? NotifyAuditFailure.DELIVERY_TIMEOUT
                            : NotifyAuditFailure.TRANSPORT_FAILED,
                    ex);
        }
    }
}
