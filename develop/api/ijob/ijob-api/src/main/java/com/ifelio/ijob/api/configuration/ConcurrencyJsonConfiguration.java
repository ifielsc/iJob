package com.ifelio.ijob.api.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceDTO;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceUpsertRequest;
import com.ifelio.ijob.contract.concurrency.PluginConcurrencyPolicyDTO;
import com.ifelio.ijob.contract.concurrency.PluginConcurrencyPolicyUpdateRequest;
import com.ifelio.ijob.contract.concurrency.TaskConcurrencyPolicyDTO;
import com.ifelio.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Configuration
public class ConcurrencyJsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer concurrencyIdentifiers() {
        return builder ->
                builder.mixIn(ConcurrencyResourceDTO.class, Identifiers.class)
                        .mixIn(PluginConcurrencyPolicyDTO.class, Identifiers.class)
                        .mixIn(TaskConcurrencyPolicyDTO.class, Identifiers.class)
                        .mixIn(ConcurrencyResourceUpsertRequest.class, Inputs.class)
                        .mixIn(PluginConcurrencyPolicyUpdateRequest.class, Inputs.class)
                        .mixIn(TaskConcurrencyPolicyUpdateRequest.class, Inputs.class);
    }

    abstract static class Identifiers {
        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
        public abstract Integer getTaskMaxConcurrency();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTaskId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getVersion();
    }

    abstract static class Inputs {
        @JsonDeserialize(using = ExactLong.class)
        public abstract void setExpectedVersion(Long value);

        @JsonDeserialize(using = ExactInteger.class)
        public abstract void setMaxConcurrency(Integer value);

        @JsonDeserialize(using = ExactInteger.class)
        public abstract void setTaskMaxConcurrency(Integer value);

        @JsonDeserialize(contentUsing = ExactLong.class)
        public abstract void setResourceIds(List<Long> value);
    }

    public static class ExactLong extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                    && parser.currentToken() != JsonToken.VALUE_STRING)
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            String value = parser.getText();
            if (!value.matches("-?(0|[1-9][0-9]*)"))
                return (Long)
                        context.handleWeirdStringValue(
                                Long.class, value, "Expected an exact decimal integer");
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException exception) {
                return (Long)
                        context.handleWeirdStringValue(
                                Long.class, value, "Integer is out of range");
            }
        }
    }

    public static class ExactInteger extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            return parser.getIntValue();
        }
    }
}
