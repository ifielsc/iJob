package com.ifelio.ijob.api.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ifelio.ijob.contract.job.DagConditionOperand;
import com.ifelio.ijob.contract.job.JobDetailResponse;
import com.ifelio.ijob.contract.job.JobEdgeResponse;
import com.ifelio.ijob.contract.job.JobListDTO;
import com.ifelio.ijob.contract.job.JobNodePolicyResponse;
import com.ifelio.ijob.contract.job.JobNodeResponse;
import com.ifelio.ijob.contract.job.JobPublicationDTO;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JobJsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jobIdentifiers() {
        return builder -> {
            for (Class<?> type :
                    new Class<?>[] {
                        JobListDTO.class,
                        JobDetailResponse.class,
                        JobNodeResponse.class,
                        JobEdgeResponse.class,
                        JobNodePolicyResponse.class
                    }) builder.mixIn(type, Identifiers.class);
            builder.mixIn(JobPublicationDTO.class, Publication.class);
            builder.mixIn(DagConditionOperand.class, Operand.class);
        };
    }

    abstract static class Identifiers {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCurrentDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCurrentRevision();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRefId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRefDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRefRevision();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCurrentRefDefinitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getCurrentRefRevision();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getFromNodeId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getToNodeId();
    }

    abstract static class Publication {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long jobId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long definitionId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long revision();
    }

    abstract static class Operand {
        @JsonDeserialize(using = PreciseLiteral.class)
        public abstract Object getLiteral();
    }

    public static class PreciseLiteral extends JsonDeserializer<Object> {
        @Override
        public Object deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            return ((ObjectMapper) parser.getCodec())
                    .readerFor(Object.class)
                    .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(parser);
        }
    }
}
