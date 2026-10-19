// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyConfigDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigListDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigView;
import com.ifielsc.ijob.core.notify.security.NotifyConfigCipher;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.mapper.notify.NotifyBindingMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyConfigMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

class NotifyConfigServiceTest {
    @Test
    void createShouldNormalizeDefaultsAndPersistOnlyCiphertext() {
        RecordingNotifyConfigMapper mapper = new RecordingNotifyConfigMapper();
        NotifyConfigCipher cipher = cipher();
        NotifyConfigService service = service(mapper, cipher);
        NotifyConfigUpsertRequest request = emailRequest();
        request.setName("  operations mail  ");

        Long id = service.create(request);

        assertEquals(201L, id);
        assertEquals("operations mail", mapper.config.getName());
        assertTrue(mapper.config.getEnabled());
        assertNotNull(mapper.config.getCreateTime());
        assertNotNull(mapper.config.getUpdateTime());
        assertTrue(cipher.isProtected(mapper.config.getConfig()));
        assertFalse(mapper.config.getConfig().contains("ops@example.com"));
    }

    @Test
    void detailShouldReturnOnlyRedactedContractView() {
        RecordingNotifyConfigMapper mapper = new RecordingNotifyConfigMapper();
        NotifyConfigCipher cipher = cipher();
        NotifyConfigValueService values = values(cipher);
        mapper.config = stored(7L, NotifyType.EMAIL, values.protect(emailRequest(), null));
        NotifyConfigService service =
                new NotifyConfigService(mapper, bindingMapper(0), values, emptyReferences());

        NotifyConfigDTO detail = service.getDetail(7L);
        NotifyConfigView.Email view =
                assertInstanceOf(NotifyConfigView.Email.class, detail.getConfig());

        assertEquals(7L, detail.getId());
        assertTrue(view.isFromConfigured());
        assertEquals(1, view.getToCount());
        assertEquals(1, view.getCcCount());
        assertFalse(detail.toString().contains("ops@example.com"));
        assertFalse(detail.toString().contains("ifjenc:v1"));
    }

    @Test
    void runtimeReadShouldReturnPlaintextCopyWithoutMutatingStoredEntity() throws Exception {
        RecordingNotifyConfigMapper mapper = new RecordingNotifyConfigMapper();
        NotifyConfigCipher cipher = cipher();
        NotifyConfigValueService values = values(cipher);
        mapper.config = stored(8L, NotifyType.EMAIL, values.protect(emailRequest(), null));
        String ciphertext = mapper.config.getConfig();
        NotifyConfigService service =
                new NotifyConfigService(mapper, bindingMapper(0), values, emptyReferences());

        NotifyConfig runtime = service.getNotifyConfig(8L);

        assertNotSame(mapper.config, runtime);
        assertTrue(runtime.getConfig().contains("ops@example.com"));
        assertEquals(ciphertext, mapper.config.getConfig());
        assertNull(service.getNotifyConfig(404L));
    }

    @Test
    void updateAndDeleteShouldRequireExistingRecord() {
        RecordingNotifyConfigMapper mapper = new RecordingNotifyConfigMapper();
        NotifyConfigCipher cipher = cipher();
        NotifyConfigValueService values = values(cipher);
        mapper.config = stored(9L, NotifyType.EMAIL, values.protect(emailRequest(), null));
        NotifyConfigService service =
                new NotifyConfigService(mapper, bindingMapper(0), values, emptyReferences());

        service.update(9L, emailRequest());
        assertEquals(9L, mapper.config.getId());
        assertTrue(cipher.isProtected(mapper.config.getConfig()));

        service.delete(9L);
        assertTrue(mapper.deleteCalled);
        assertFalse(mapper.exists);
    }

    @Test
    void invalidRequestAndMissingRecordShouldUseBusinessCodes() {
        NotifyConfigService service = service(new RecordingNotifyConfigMapper(), cipher());
        NotifyConfigUpsertRequest invalid = emailRequest();
        invalid.setName(" ");

        BusinessException parameterError =
                assertThrows(BusinessException.class, () -> service.create(invalid));
        BusinessException notFound =
                assertThrows(BusinessException.class, () -> service.getDetail(1L));

        assertEquals(ResultCode.PARAM_ERROR, parameterError.getCode());
        assertEquals(ResultCode.NOT_FOUND, notFound.getCode());
    }

    @Test
    void batchDeleteShouldRejectReferencedAndPartialMissingConfigs() {
        RecordingNotifyConfigMapper mapper = new RecordingNotifyConfigMapper();
        mapper.config = stored(9L, NotifyType.EMAIL, "protected");
        NotifyConfigService referenced =
                new NotifyConfigService(
                        mapper, bindingMapper(1), values(cipher()), emptyReferences());
        BatchDeleteRequest one = new BatchDeleteRequest();
        one.setIds(List.of(9L));

        BusinessException conflict =
                assertThrows(BusinessException.class, () -> referenced.deleteBatch(one));
        assertEquals(ResultCode.CONFLICT, conflict.getCode());
        assertFalse(mapper.deleteCalled);

        NotifyConfigService partial =
                new NotifyConfigService(
                        mapper, bindingMapper(0), values(cipher()), emptyReferences());
        BatchDeleteRequest two = new BatchDeleteRequest();
        two.setIds(List.of(9L, 10L));
        BusinessException missing =
                assertThrows(BusinessException.class, () -> partial.deleteBatch(two));
        assertEquals(ResultCode.NOT_FOUND, missing.getCode());
        assertFalse(mapper.deleteCalled);
    }

    private NotifyConfigService service(NotifyConfigMapper mapper, NotifyConfigCipher cipher) {
        return new NotifyConfigService(mapper, bindingMapper(0), values(cipher), emptyReferences());
    }

    private NotifyBindingMapper bindingMapper(int referenceCount) {
        return (NotifyBindingMapper)
                Proxy.newProxyInstance(
                        NotifyBindingMapper.class.getClassLoader(),
                        new Class<?>[] {NotifyBindingMapper.class},
                        (proxy, method, arguments) ->
                                method.getName().equals("countByNotifyConfigIds")
                                        ? referenceCount
                                        : null);
    }

    private NotifyConfigValueService values(NotifyConfigCipher cipher) {
        return new NotifyConfigValueService(
                cipher,
                new ObjectMapper(),
                com.ifielsc.ijob.core.notify.channel.email.MailChannelTestSupport.channel(
                        "sender@example.com"));
    }

    private NotifyConfigUpsertRequest emailRequest() {
        NotifyConfigRequest.Email email = new NotifyConfigRequest.Email();
        email.setMailChannelId(1L);
        email.setFrom("sender@example.com");
        email.setTo(List.of("ops@example.com"));
        email.setCc(List.of("audit@example.com"));
        NotifyConfigUpsertRequest request = new NotifyConfigUpsertRequest();
        request.setName("operations mail");
        request.setType(NotifyType.EMAIL);
        request.setEmailConfig(email);
        return request;
    }

    private NotifyConfig stored(Long id, NotifyType type, String config) {
        NotifyConfig value = new NotifyConfig();
        value.setId(id);
        value.setName("stored");
        value.setType(type);
        value.setConfig(config);
        value.setEnabled(true);
        return value;
    }

    private NotifyConfigCipher cipher() {
        AppProperties properties = new AppProperties();
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 21);
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(key));
        properties.getSecurity().getAes().setKeyId("test");
        return new NotifyConfigCipher(properties);
    }

    private com.ifielsc.ijob.core.task.definition.DataCheckReferences emptyReferences() {
        return new com.ifielsc.ijob.core.task.definition.DataCheckReferences(
                null,
                null,
                new com.ifielsc.ijob.infra.mapper.task.TaskDefinitionResourceReferenceMapper() {
                    public Long selectMaxDefinitionId() {
                        return null;
                    }

                    public java.util.List<com.ifielsc.ijob.infra.entity.task.TaskDefinition>
                            selectDefinitionBatch(long after, long upper) {
                        return java.util.List.of();
                    }
                });
    }

    private static final class RecordingNotifyConfigMapper implements NotifyConfigMapper {
        @Override
        public java.util.List<com.ifielsc.ijob.contract.notify.NotifyConfigListDTO>
                selectSummariesByIds(java.util.List<Long> ids) {
            throw new UnsupportedOperationException("Unexpected summary lookup");
        }

        private NotifyConfig config;
        private boolean exists = true;
        private boolean deleteCalled;

        @Override
        public List<NotifyConfigListDTO> selectDtoList(NotifyConfigPageQuery query) {
            return List.of();
        }

        @Override
        public NotifyConfig selectById(Long id) {
            return exists && config != null && id.equals(config.getId()) ? config : null;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return exists && config != null && ids.contains(config.getId())
                    ? List.of(config.getId())
                    : List.of();
        }

        @Override
        public int countById(Long id) {
            return selectById(id) == null ? 0 : 1;
        }

        @Override
        public int insert(NotifyConfig inserted) {
            inserted.setId(201L);
            config = inserted;
            exists = true;
            return 1;
        }

        @Override
        public int updateById(NotifyConfig updated) {
            config = updated;
            return 1;
        }

        @Override
        public int deleteById(Long id) {
            deleteCalled = true;
            exists = false;
            return 1;
        }

        @Override
        public int deleteByIds(List<Long> ids) {
            deleteCalled = true;
            exists = false;
            return ids.size();
        }
    }
}
