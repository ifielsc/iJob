package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceConfigRequest;
import com.ifielsc.ijob.contract.resource.ResourceConfigView;
import com.ifielsc.ijob.contract.resource.ResourceDTO;
import com.ifielsc.ijob.contract.resource.ResourceListDTO;
import com.ifielsc.ijob.contract.resource.ResourcePageQuery;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;
import com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationContext;
import com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationValidationService;
import com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationValidator;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.resource.ResourceMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionResourceReferenceMapper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

class ResourceServiceTest {
    @Test
    void createShouldNormalizeDefaultsAndPersistOnlyCiphertext() {
        RecordingResourceMapper mapper = new RecordingResourceMapper();
        ResourceConfigCipher cipher = cipher();
        ResourceService service = service(mapper, cipher);
        ResourceUpsertRequest request = dbRequest("database-secret");
        request.setCode("  demo-db  ");
        request.setName("  Demo DB  ");

        Long id = service.create(request);

        assertEquals(101L, id);
        assertEquals("demo-db", mapper.resource.getCode());
        assertEquals("Demo DB", mapper.resource.getName());
        assertTrue(mapper.resource.getEnabled());
        assertNotNull(mapper.resource.getCreateTime());
        assertNotNull(mapper.resource.getUpdateTime());
        assertTrue(cipher.isProtected(mapper.resource.getConfig()));
        assertFalse(mapper.resource.getConfig().contains("database-secret"));
    }

    @Test
    void detailShouldReturnMaskedContractViewWithoutStoredConfig() {
        RecordingResourceMapper mapper = new RecordingResourceMapper();
        ResourceConfigCipher cipher = cipher();
        ResourceConfigService configService = new ResourceConfigService(cipher);
        ResourceUpsertRequest request = dbRequest("database-secret");
        mapper.resource = storedResource(7L, configService.protect(request, null));
        ResourceService service =
                new ResourceService(
                        mapper,
                        configService,
                        emptyReferences(),
                        pluginValidation(mapper),
                        event -> {});

        ResourceDTO detail = service.getDetail(7L);
        ResourceConfigView.Db view =
                assertInstanceOf(ResourceConfigView.Db.class, detail.getConfig());

        assertEquals(7L, detail.getId());
        assertEquals("stored", detail.getName());
        assertTrue(view.isPasswordConfigured());
        assertFalse(detail.toString().contains("database-secret"));
        assertFalse(detail.toString().contains("ifjenc:v1"));
    }

    @Test
    void createShouldRejectDuplicateCode() {
        RecordingResourceMapper mapper = new RecordingResourceMapper();
        mapper.duplicate = storedResource(9L, "protected");
        ResourceService service = service(mapper, cipher());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.create(dbRequest("secret")));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertEquals("Resource code already exists", exception.getMessage());
    }

    @Test
    void updateAndDeleteShouldRequireExistingResource() {
        RecordingResourceMapper mapper = new RecordingResourceMapper();
        ResourceConfigCipher cipher = cipher();
        ResourceConfigService configService = new ResourceConfigService(cipher);
        mapper.resource = storedResource(8L, configService.protect(dbRequest("old-secret"), null));
        List<Object> events = new ArrayList<>();
        ResourceService service =
                new ResourceService(
                        mapper,
                        configService,
                        emptyReferences(),
                        pluginValidation(mapper),
                        events::add);

        service.update(8L, dbRequest(null));
        assertEquals(8L, mapper.resource.getId());
        assertTrue(cipher.isProtected(mapper.resource.getConfig()));

        service.delete(8L);
        assertTrue(mapper.deleteCalled);
        assertFalse(mapper.exists);
        assertEquals(2, events.size());
        assertInstanceOf(ResourceCacheInvalidationEvent.class, events.get(0));
    }

    @Test
    void invalidCodeShouldReturnParameterErrorBeforePersistence() {
        ResourceService service = service(new RecordingResourceMapper(), cipher());
        ResourceUpsertRequest request = dbRequest("secret");
        request.setCode("invalid code");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    void batchDeleteShouldRejectPartialMissingSetBeforeDelete() {
        RecordingResourceMapper mapper = new RecordingResourceMapper();
        mapper.resource = storedResource(7L, "protected");
        ResourceService service = service(mapper, cipher());
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(7L, 8L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteBatch(request));

        assertEquals(ResultCode.NOT_FOUND, exception.getCode());
        assertFalse(mapper.deleteCalled);
    }

    @Test
    void batchDeleteShouldProtectImmutableDefinitionReferences() {
        RecordingResourceMapper mapper = new RecordingResourceMapper();
        mapper.resource = storedResource(7L, "protected");
        TaskDefinition definition = new TaskDefinition();
        definition.setId(101L);
        definition.setTaskId(11L);
        definition.setPluginType(PluginType.SQL);
        definition.setPluginConfig("opaque-secret-config");
        TaskPluginConfigPublicationValidator validator =
                new TaskPluginConfigPublicationValidator() {
                    @Override
                    public Set<PluginType> supportTypes() {
                        return Set.of(PluginType.SQL);
                    }

                    @Override
                    public void validate(
                            PluginType type,
                            String pluginConfig,
                            TaskPluginConfigPublicationContext context) {
                        // The guard only needs immutable reference extraction.
                    }

                    @Override
                    public Set<Long> referencedResourceIds(PluginType type, String pluginConfig) {
                        return Set.of(7L);
                    }
                };
        ResourceService service =
                new ResourceService(
                        mapper,
                        new ResourceConfigService(cipher()),
                        references(List.of(definition)),
                        new TaskPluginConfigPublicationValidationService(
                                List.of(validator), mapper, null, null),
                        event -> {});
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(7L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deleteBatch(request));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertFalse(exception.getMessage().contains("7"));
        assertFalse(exception.getMessage().contains("opaque-secret-config"));
        assertFalse(mapper.deleteCalled);
    }

    private ResourceService service(ResourceMapper mapper, ResourceConfigCipher cipher) {
        return new ResourceService(
                mapper,
                new ResourceConfigService(cipher),
                emptyReferences(),
                pluginValidation(mapper),
                event -> {});
    }

    private TaskDefinitionResourceReferenceMapper emptyReferences() {
        return references(List.of());
    }

    @Test
    void deletionScansEveryBatchAndStopsAtTheFirstHistoricalReference() {
        var mapper = new RecordingResourceMapper();
        mapper.resource = storedResource(7L, "protected");
        var definitions = new ArrayList<TaskDefinition>();
        for (long id = 1; id <= 1001; id++) {
            var definition = new TaskDefinition();
            definition.setId(id);
            definition.setPluginType(PluginType.SQL);
            definition.setPluginConfig(id == 1001 ? "referenced" : "unused");
            definitions.add(definition);
        }
        var parsed = new java.util.concurrent.atomic.AtomicInteger();
        var validator =
                new TaskPluginConfigPublicationValidator() {
                    public Set<PluginType> supportTypes() {
                        return Set.of(PluginType.SQL);
                    }

                    public void validate(
                            PluginType type,
                            String config,
                            TaskPluginConfigPublicationContext context) {}

                    public Set<Long> referencedResourceIds(PluginType type, String config) {
                        parsed.incrementAndGet();
                        return config.equals("referenced") ? Set.of(7L) : Set.of();
                    }
                };
        var service =
                new ResourceService(
                        mapper,
                        new ResourceConfigService(cipher()),
                        references(definitions),
                        new TaskPluginConfigPublicationValidationService(
                                List.of(validator), mapper, null, null),
                        event -> {});
        assertEquals(
                ResultCode.CONFLICT,
                assertThrows(BusinessException.class, () -> service.delete(7L)).getCode());
        assertEquals(1001, parsed.get());
        assertFalse(mapper.deleteCalled);
        assertTrue(com.github.pagehelper.PageHelper.getLocalPage() == null);
        definitions.getFirst().setPluginConfig("referenced");
        parsed.set(0);
        assertThrows(BusinessException.class, () -> service.delete(7L));
        assertEquals(1, parsed.get());
        definitions.forEach(d -> d.setPluginConfig("unused"));
        parsed.set(0);
        service.delete(7L);
        assertEquals(1001, parsed.get());
        assertTrue(mapper.deleteCalled);
    }

    @Test
    void malformedHistoryOrFailedBatchCannotPermitDeletionOrLeakPagination() {
        for (int scenario = 0; scenario < 3; scenario++) {
            var mapper = new RecordingResourceMapper();
            mapper.resource = storedResource(7L, "protected");
            final int selected = scenario;
            var references =
                    new TaskDefinitionResourceReferenceMapper() {
                        public Long selectMaxDefinitionId() {
                            return 3L;
                        }

                        public List<TaskDefinition> selectDefinitionBatch(
                                long afterId, long upperId) {
                            if (selected == 0)
                                throw new IllegalStateException("synthetic query failure");
                            if (selected == 1) return null;
                            return List.of(new TaskDefinition());
                        }
                    };
            var service =
                    new ResourceService(
                            mapper,
                            new ResourceConfigService(cipher()),
                            references,
                            pluginValidation(mapper),
                            event -> {});
            assertThrows(RuntimeException.class, () -> service.delete(7L));
            assertFalse(mapper.deleteCalled);
            assertTrue(com.github.pagehelper.PageHelper.getLocalPage() == null);
        }
    }

    private TaskDefinitionResourceReferenceMapper references(List<TaskDefinition> definitions) {
        return new TaskDefinitionResourceReferenceMapper() {
            public Long selectMaxDefinitionId() {
                return definitions.stream()
                        .map(TaskDefinition::getId)
                        .max(Long::compareTo)
                        .orElse(null);
            }

            public List<TaskDefinition> selectDefinitionBatch(long afterId, long upperId) {
                assertEquals(500, com.github.pagehelper.PageHelper.getLocalPage().getPageSize());
                assertFalse(com.github.pagehelper.PageHelper.getLocalPage().isCount());
                return definitions.stream()
                        .filter(d -> d.getId() > afterId && d.getId() <= upperId)
                        .sorted(java.util.Comparator.comparing(TaskDefinition::getId))
                        .limit(500)
                        .toList();
            }
        };
    }

    private TaskPluginConfigPublicationValidationService pluginValidation(ResourceMapper mapper) {
        return new TaskPluginConfigPublicationValidationService(List.of(), mapper, null, null);
    }

    private ResourceUpsertRequest dbRequest(String password) {
        ResourceConfigRequest.Db config = new ResourceConfigRequest.Db();
        config.setDbType("MYSQL");
        config.setJdbcUrl("jdbc:mysql://127.0.0.1/demo");
        config.setUsername("demo");
        config.setPassword(password);
        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setCode("demo-db");
        request.setName("Demo DB");
        request.setType(ResourceType.DB);
        request.setDbConfig(config);
        return request;
    }

    private Resource storedResource(Long id, String config) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setCode("demo-db");
        resource.setName("stored");
        resource.setType(ResourceType.DB);
        resource.setConfig(config);
        resource.setEnabled(true);
        return resource;
    }

    private ResourceConfigCipher cipher() {
        AppProperties properties = new AppProperties();
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 11);
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(key));
        properties.getSecurity().getAes().setKeyId("test");
        return new ResourceConfigCipher(properties);
    }

    private static final class RecordingResourceMapper implements ResourceMapper {
        @Override
        public java.util.List<com.ifielsc.ijob.contract.resource.ResourceListDTO>
                selectSummariesByIds(java.util.List<Long> ids) {
            throw new UnsupportedOperationException("Unexpected summary lookup");
        }

        private Resource resource;
        private Resource duplicate;
        private boolean exists = true;
        private boolean deleteCalled;

        @Override
        public List<ResourceListDTO> selectDtoList(ResourcePageQuery query) {
            return List.of();
        }

        @Override
        public Resource selectById(Long id) {
            return exists && resource != null && id.equals(resource.getId()) ? resource : null;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return exists && resource != null && ids.contains(resource.getId())
                    ? List.of(resource.getId())
                    : List.of();
        }

        @Override
        public Resource selectByCode(String code) {
            return duplicate;
        }

        @Override
        public int insert(Resource inserted) {
            inserted.setId(101L);
            resource = inserted;
            exists = true;
            return 1;
        }

        @Override
        public int updateById(Resource updated) {
            resource = updated;
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
