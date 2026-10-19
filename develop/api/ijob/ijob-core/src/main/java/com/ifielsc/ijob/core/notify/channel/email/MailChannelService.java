package com.ifielsc.ijob.core.notify.channel.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.notify.MailChannelDTO;
import com.ifielsc.ijob.contract.notify.MailChannelPageQuery;
import com.ifielsc.ijob.contract.notify.MailChannelRequest;
import com.ifielsc.ijob.contract.notify.MailConnectionDTO;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.core.notify.security.NotifyConfigCipher;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.core.security.AesGcmConfigProtector;
import com.ifielsc.ijob.infra.entity.notify.MailChannel;
import com.ifielsc.ijob.infra.mapper.notify.MailChannelMapper;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.beans.BeanUtils;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

@Service
public class MailChannelService {
    private final MailChannelMapper mapper;
    private final ObjectMapper json;
    private final AesGcmConfigProtector cipher;
    private final NotifyConfigCipher notificationCipher;

    public MailChannelService(
            MailChannelMapper mapper,
            ObjectMapper json,
            AppProperties properties,
            NotifyConfigCipher notificationCipher) {
        this.mapper = mapper;
        this.notificationCipher = notificationCipher;
        this.json = json;
        this.cipher = new AesGcmConfigProtector(properties, "ifj_mail_channel.config:v1");
    }

    public PageResult<MailChannelDTO> queryPage(MailChannelPageQuery query) {
        MailChannelPageQuery resolved = query == null ? new MailChannelPageQuery() : query;
        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        try {
            // Preserve PageHelper metadata before mapping the protected configuration.
            var page = PageResults.from(mapper.selectList(resolved));
            PageResult<MailChannelDTO> result = new PageResult<>();
            BeanUtils.copyProperties(page, result, "list");
            result.setList(page.getList().stream().map(this::view).toList());
            return result;
        } finally {
            PageHelper.clearPage();
        }
    }

    public MailChannelDTO getDetail(Long id) {
        return view(require(id));
    }

    public MailChannelDTO getEnabledDetail(Long id) {
        if (id == null || id <= 0) throw error("Invalid mail channel ID");
        MailChannel row = mapper.selectByIdForUpdate(id);
        if (row == null)
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Mail channel does not exist");
        if (!Boolean.TRUE.equals(row.getEnabled())) throw error("Mail channel is disabled");
        return view(row);
    }

    @Transactional
    public Long create(MailChannelRequest request) {
        MailChannel row = prepare(request, null);
        row.setRevision(1L);
        row.setCreateTime(row.getUpdateTime());
        if (mapper.insert(row) != 1 || row.getId() == null)
            throw error("Mail channel creation failed");
        return row.getId();
    }

    @Transactional
    public void update(Long id, MailChannelRequest request) {
        MailChannel previous = require(id);
        if (request == null
                || request.getRevision() == null
                || !request.getRevision().equals(previous.getRevision())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Mail channel changed; reload before saving");
        }
        MailChannel row = prepare(request, previous);
        row.setId(id);
        row.setRevision(request.getRevision());
        if (mapper.updateById(row) != 1)
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Mail channel changed; reload before saving");
    }

    @Transactional
    public void delete(Long id) {
        deleteIds(BatchDeleteValidator.validate(BatchDeleteValidator.single(id)));
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        deleteIds(BatchDeleteValidator.validate(request));
    }

    public Delivery delivery(Long id) {
        MailChannel row = require(id);
        if (!Boolean.TRUE.equals(row.getEnabled())) throw error("Mail channel is disabled");
        MailChannelRequest settings = read(row);
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        sender.setPort(settings.getPort());
        sender.setDefaultEncoding("UTF-8");
        if (Boolean.TRUE.equals(settings.getAuthentication())) {
            sender.setUsername(settings.getUsername());
            sender.setPassword(settings.getPassword());
        }
        Properties props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", String.valueOf(settings.getAuthentication()));
        props.setProperty("mail.smtp.connectiontimeout", "5000");
        props.setProperty("mail.smtp.timeout", "10000");
        props.setProperty("mail.smtp.writetimeout", "10000");
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty(
                "mail.smtp.ssl.enable", String.valueOf("TLS".equals(settings.getSecurity())));
        props.setProperty(
                "mail.smtp.starttls.enable",
                String.valueOf("STARTTLS".equals(settings.getSecurity())));
        props.setProperty(
                "mail.smtp.starttls.required",
                String.valueOf("STARTTLS".equals(settings.getSecurity())));
        return new Delivery(sender, settings.getFrom());
    }

    public MailConnectionDTO testConnection(Long id) {
        Delivery delivery = delivery(id);
        try {
            delivery.sender().testConnection();
            return new MailConnectionDTO(true, null);
        } catch (AuthenticationFailedException exception) {
            return new MailConnectionDTO(false, "AUTHENTICATION_FAILED");
        } catch (Exception exception) {
            return new MailConnectionDTO(false, "CONNECTION_FAILED");
        }
    }

    private void deleteIds(List<Long> ids) {
        // Notification writes acquire the same channel lock before saving a reference.
        for (Long id : ids) {
            if (mapper.selectByIdForUpdate(id) == null)
                throw BusinessException.localized(
                        ResultCode.NOT_FOUND, "Mail channel does not exist");
        }
        requireUnreferenced(ids, mapper.selectEmailConfigurations());
        requireUnreferenced(ids, mapper.selectPendingEmailSnapshots());
        if (mapper.deleteByIds(ids) != ids.size())
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Mail channel deletion conflict");
    }

    private void requireUnreferenced(List<Long> ids, List<String> snapshots) {
        for (String snapshot : snapshots) {
            Long channelId;
            try {
                var config = json.readTree(notificationCipher.decrypt(snapshot));
                if (config == null || !config.isObject()) throw new IllegalArgumentException();
                var node = config.get("mailChannelId");
                channelId = node == null || node.isNull() ? null : Long.valueOf(node.asText());
            } catch (Exception exception) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Mail channel references cannot be verified");
            }
            if (ids.contains(channelId))
                throw BusinessException.localized(
                        ResultCode.CONFLICT,
                        "Mail channel is still referenced by notifications or pending deliveries");
        }
    }

    private MailChannel prepare(MailChannelRequest request, MailChannel previous) {
        if (request == null) throw error("Mail channel is required");
        String name = required(request.getName(), 128);
        String host = required(request.getHost(), 253);
        if (!host.matches("[A-Za-z0-9.:-]+") || host.contains(".."))
            throw error("Invalid SMTP host");
        if (request.getPort() == null || request.getPort() < 1 || request.getPort() > 65535)
            throw error("Invalid SMTP port");
        if (request.getSecurity() == null
                || !List.of("NONE", "STARTTLS", "TLS").contains(request.getSecurity()))
            throw error("Invalid SMTP encryption mode");
        String from = required(request.getFrom(), 320);
        try {
            InternetAddress address = new InternetAddress(from, true);
            address.validate();
            if (!from.equals(address.getAddress()) || address.isGroup())
                throw error("Invalid sender address");
        } catch (jakarta.mail.internet.AddressException exception) {
            throw error("Invalid sender address");
        }
        boolean authentication = !Boolean.FALSE.equals(request.getAuthentication());
        String username = authentication ? required(request.getUsername(), 320) : null;
        String password = request.getPassword();
        if (authentication) {
            if (password == null && previous != null) {
                MailChannelRequest old = read(previous);
                // A credential must not silently move to a different SMTP account/server.
                if (!host.equals(old.getHost())
                        || !username.equals(old.getUsername())
                        || !Boolean.TRUE.equals(old.getAuthentication())) {
                    throw error(
                            "Provide a new authorization code when changing SMTP host or username");
                }
                password = old.getPassword();
            }
            if (password == null
                    || password.isBlank()
                    || password.length() > 4096
                    || password.contains("\r")
                    || password.contains("\n")) throw error("SMTP authorization code is required");
        } else password = null;
        MailChannelRequest settings = new MailChannelRequest();
        settings.setHost(host);
        settings.setPort(request.getPort());
        settings.setSecurity(request.getSecurity());
        settings.setAuthentication(authentication);
        settings.setUsername(username);
        settings.setPassword(password);
        settings.setFrom(from);
        MailChannel row = new MailChannel();
        row.setName(name);
        row.setEnabled(!Boolean.FALSE.equals(request.getEnabled()));
        row.setUpdateTime(LocalDateTime.now());
        try {
            row.setConfig(cipher.encrypt(json.writeValueAsString(settings)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw error("Invalid mail channel configuration");
        }
        return row;
    }

    private MailChannelDTO view(MailChannel row) {
        MailChannelRequest settings = read(row);
        MailChannelDTO dto = new MailChannelDTO();
        BeanUtils.copyProperties(settings, dto);
        dto.setId(row.getId());
        dto.setName(row.getName());
        dto.setEnabled(row.getEnabled());
        dto.setRevision(row.getRevision());
        dto.setUpdateTime(row.getUpdateTime());
        dto.setPasswordConfigured(
                settings.getPassword() != null && !settings.getPassword().isBlank());
        return dto;
    }

    private MailChannelRequest read(MailChannel row) {
        try {
            return json.readValue(cipher.decrypt(row.getConfig()), MailChannelRequest.class);
        } catch (Exception exception) {
            throw error("Mail channel configuration cannot be read");
        }
    }

    private MailChannel require(Long id) {
        if (id == null || id <= 0) throw error("Invalid mail channel ID");
        MailChannel row = mapper.selectById(id);
        if (row == null)
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Mail channel does not exist");
        return row;
    }

    private String required(String value, int max) {
        if (value == null
                || value.isBlank()
                || value.trim().length() > max
                || value.contains("\r")
                || value.contains("\n"))
            throw error("Mail channel contains an invalid required field");
        return value.trim();
    }

    private BusinessException error(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    public record Delivery(JavaMailSenderImpl sender, String from) {}
}
