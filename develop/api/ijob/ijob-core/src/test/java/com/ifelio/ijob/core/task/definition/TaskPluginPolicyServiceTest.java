package com.ifelio.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

class TaskPluginPolicyServiceTest {
    @Test
    void absentAvailabilityProviderShouldPreserveEnabledBehavior() {
        TaskPluginAvailabilityService service = new TaskPluginAvailabilityService(List.of());

        assertTrue(service.isEnabled(PluginType.SCRIPT));
    }

    @Test
    void disabledPluginShouldRejectNewPublication() {
        TaskPluginAvailabilityService service =
                new TaskPluginAvailabilityService(List.of(availability(PluginType.ETL, false)));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.requireEnabledForPublication(PluginType.ETL));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertEquals("Plugin is disabled: ETL", exception.getMessage());
    }

    @Test
    void duplicateAvailabilityShouldFailFast() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new TaskPluginAvailabilityService(
                                        List.of(
                                                availability(PluginType.SQL, true),
                                                availability(PluginType.SQL, false))));

        assertEquals("duplicate task plugin availability: SQL", exception.getMessage());
    }

    @Test
    void publicationValidatorShouldReceiveExecutionPolicy() {
        AtomicReference<TaskPluginConfigPublicationContext> captured = new AtomicReference<>();
        TaskPluginConfigPublicationValidator validator =
                validator(Set.of(PluginType.SQL), captured);
        TaskPluginConfigPublicationValidationService service =
                new TaskPluginConfigPublicationValidationService(
                        List.of(validator), null, null, null);

        service.validate(PluginType.SQL, "{}", new TaskPluginConfigPublicationContext(3, 60));

        assertEquals(3, captured.get().retryTimes());
        assertEquals(60, captured.get().timeout());
    }

    @Test
    void invalidPublicationConfigShouldBecomeParameterError() {
        TaskPluginConfigPublicationValidator validator =
                new TaskPluginConfigPublicationValidator() {
                    @Override
                    public Set<PluginType> supportTypes() {
                        return Set.of(PluginType.EVENT);
                    }

                    @Override
                    public void validate(
                            PluginType type,
                            String pluginConfig,
                            TaskPluginConfigPublicationContext context) {
                        throw new IllegalArgumentException("bad event");
                    }
                };
        TaskPluginConfigPublicationValidationService service =
                new TaskPluginConfigPublicationValidationService(
                        List.of(validator), null, null, null);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.validate(
                                        PluginType.EVENT,
                                        "{}",
                                        new TaskPluginConfigPublicationContext(0, null)));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertEquals("Plugin configuration is invalid", exception.getMessage());
        assertEquals("bad event", exception.getCause().getMessage());
    }

    private TaskPluginAvailability availability(PluginType supported, boolean enabled) {
        return new TaskPluginAvailability() {
            @Override
            public boolean supports(PluginType type) {
                return type == supported;
            }

            @Override
            public boolean isEnabled(PluginType type) {
                return enabled;
            }
        };
    }

    private TaskPluginConfigPublicationValidator validator(
            Set<PluginType> types, AtomicReference<TaskPluginConfigPublicationContext> captured) {
        return new TaskPluginConfigPublicationValidator() {
            @Override
            public Set<PluginType> supportTypes() {
                return types;
            }

            @Override
            public void validate(
                    PluginType type,
                    String pluginConfig,
                    TaskPluginConfigPublicationContext context) {
                captured.set(context);
            }
        };
    }
}
