package com.ifielsc.ijob.api.configuration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ifielsc.ijob.contract.calendar.CalendarDetailDTO;
import com.ifielsc.ijob.contract.calendar.CalendarExceptionDTO;
import com.ifielsc.ijob.contract.calendar.CalendarListDTO;
import com.ifielsc.ijob.contract.notify.MailChannelDTO;
import com.ifielsc.ijob.contract.notify.MailChannelRequest;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigListDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigView;
import com.ifielsc.ijob.contract.notify.NotifyTestSendDTO;
import com.ifielsc.ijob.contract.resource.ResourceConnectionTestDTO;
import com.ifielsc.ijob.contract.resource.ResourceDTO;
import com.ifielsc.ijob.contract.resource.ResourceListDTO;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Management identifiers remain exact in JavaScript without changing domain or persistence types.
 */
@Configuration
public class ConfigurationJsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer configurationIdentifiers() {
        return builder -> {
            for (Class<?> type :
                    new Class<?>[] {
                        ResourceDTO.class,
                        ResourceListDTO.class,
                        CalendarDetailDTO.class,
                        CalendarListDTO.class,
                        CalendarExceptionDTO.class,
                        NotifyConfigDTO.class,
                        NotifyConfigListDTO.class
                    }) {
                builder.mixIn(type, Identifier.class);
            }
            builder.mixIn(ResourceConnectionTestDTO.class, ResourceIdentifier.class);
            builder.mixIn(NotifyTestSendDTO.class, NotifyIdentifier.class);
            builder.mixIn(NotifyBindingDTO.class, BindingIdentifier.class);
            builder.mixIn(
                    com.ifielsc.ijob.contract.parameter.ParameterTemplateDTO.class,
                    MailChannelIdentifier.class);
            builder.mixIn(
                    com.ifielsc.ijob.contract.parameter.ParameterTemplateRequest.class,
                    MailChannelInput.class);
            builder.mixIn(
                    com.ifielsc.ijob.contract.parameter.ParameterForm.class,
                    ParameterFormIdentifier.class);
            builder.mixIn(
                    com.ifielsc.ijob.contract.parameter.ParameterField.class,
                    ParameterFieldInput.class);
            builder.mixIn(MailChannelDTO.class, MailChannelIdentifier.class);
            builder.mixIn(MailChannelRequest.class, MailChannelInput.class);
            builder.mixIn(NotifyConfigView.Email.class, EmailChannelIdentifier.class);
            builder.mixIn(NotifyConfigRequest.Email.class, EmailChannelInput.class);
        };
    }

    abstract static class ParameterFormIdentifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTemplateId();

        @JsonDeserialize(using = ConcurrencyJsonConfiguration.ExactLong.class)
        public abstract void setTemplateId(Long value);
    }

    abstract static class ParameterFieldInput {
        @JsonDeserialize(using = ParameterDefaultValue.class)
        public abstract void setDefaultValue(Object value);
    }

    public static class ParameterDefaultValue
            extends com.fasterxml.jackson.databind.JsonDeserializer<Object> {
        @Override
        public Object deserialize(
                com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context)
                throws java.io.IOException {
            return ((com.fasterxml.jackson.databind.ObjectMapper) parser.getCodec())
                    .readerFor(Object.class)
                    .with(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(parser);
        }
    }

    abstract static class Identifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getId();
    }

    abstract static class MailChannelIdentifier extends Identifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getRevision();
    }

    abstract static class EmailChannelIdentifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getMailChannelId();
    }

    abstract static class MailChannelInput {
        @JsonDeserialize(using = ConcurrencyJsonConfiguration.ExactLong.class)
        public abstract void setRevision(Long value);
    }

    abstract static class EmailChannelInput {
        @JsonDeserialize(using = ConcurrencyJsonConfiguration.ExactLong.class)
        public abstract void setMailChannelId(Long value);
    }

    abstract static class BindingIdentifier extends Identifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getTargetId();

        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getNotifyConfigId();
    }

    abstract static class ResourceIdentifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getResourceId();
    }

    abstract static class NotifyIdentifier {
        @JsonSerialize(using = ToStringSerializer.class)
        public abstract Long getNotifyConfigId();
    }
}
