package com.ifelio.ijob.api.configuration;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ifelio.ijob.contract.event.EventWaitDTO;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keeps approval identifiers exact without changing arbitrary JSON numeric values. */
@Configuration
public class EventJsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer eventIdentifiers() {
        return builder ->
                builder.mixIn(EventWaitDTO.class, WaitIdentifiers.class)
                        .mixIn(
                                com.ifelio.ijob.contract.event.EventBindingDTO.class,
                                BindingIdentifiers.class);
    }

    abstract static class BindingIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTargetId();
    }

    abstract static class WaitIdentifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getInstanceId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRootInstanceId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getMatchedEventId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTaskId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTaskDefinitionId();
    }
}
