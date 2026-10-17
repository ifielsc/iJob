package com.ifelio.ijob.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.contract.calendar.CalendarExceptionDTO;
import com.ifelio.ijob.contract.notify.MailChannelDTO;
import com.ifelio.ijob.contract.notify.MailChannelRequest;
import com.ifelio.ijob.contract.notify.NotifyConfigDTO;
import com.ifelio.ijob.contract.notify.NotifyConfigRequest;
import com.ifelio.ijob.contract.notify.NotifyConfigView;
import com.ifelio.ijob.contract.resource.ResourceDTO;
import com.ifelio.ijob.contract.resource.ResourceListDTO;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ConfigurationJsonConfigurationTest {
    @Test
    void parameterIdentifiersAndDecimalsRoundTripExactly() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new ConfigurationJsonConfiguration().configurationIdentifiers().customize(builder);
        var mapper = builder.build();
        var dto = new com.ifelio.ijob.contract.parameter.ParameterTemplateDTO();
        dto.setId(Long.MAX_VALUE);
        dto.setRevision(1L);
        assertEquals("9223372036854775807", mapper.valueToTree(dto).path("id").textValue());
        var form =
                mapper.readValue(
                        "{\"templateId\":\"9223372036854775807\",\"fields\":[{\"name\":\"x\",\"type\":\"DECIMAL\",\"defaultValue\":0.1234567890123456789}]}",
                        com.ifelio.ijob.contract.parameter.ParameterForm.class);
        assertEquals(Long.MAX_VALUE, form.getTemplateId());
        assertEquals(
                new java.math.BigDecimal("0.1234567890123456789"),
                form.getFields().getFirst().getDefaultValue());
        assertEquals(
                "9223372036854775807", mapper.valueToTree(form).path("templateId").textValue());
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () ->
                        mapper.readValue(
                                "{\"templateId\":1.5}",
                                com.ifelio.ijob.contract.parameter.ParameterForm.class));
    }

    @Test
    void mailIdentifiersAndRevisionsStayExactAndFractionalInputsAreRejected() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new ConfigurationJsonConfiguration().configurationIdentifiers().customize(builder);
        var mapper = builder.build();
        var channel = new MailChannelDTO();
        channel.setId(Long.MAX_VALUE);
        channel.setRevision(Long.MAX_VALUE);
        var tree = mapper.valueToTree(channel);
        assertEquals("9223372036854775807", tree.path("id").textValue());
        assertEquals("9223372036854775807", tree.path("revision").textValue());
        var email = new NotifyConfigView.Email();
        email.setMailChannelId(Long.MAX_VALUE);
        assertEquals(
                "9223372036854775807", mapper.valueToTree(email).path("mailChannelId").textValue());
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.readValue("{\"revision\":1.5}", MailChannelRequest.class));
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.readValue("{\"mailChannelId\":1.5}", NotifyConfigRequest.Email.class));
    }

    @Test
    void managementIdentifiersShouldSerializeWithoutJavascriptPrecisionLoss() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new ConfigurationJsonConfiguration().configurationIdentifiers().customize(builder);
        var mapper = builder.build();
        ResourceDTO resource = new ResourceDTO();
        resource.setId(Long.MAX_VALUE);
        ResourceListDTO list = new ResourceListDTO();
        list.setId(Long.MAX_VALUE);
        CalendarExceptionDTO exception = new CalendarExceptionDTO();
        exception.setId(Long.MAX_VALUE);
        NotifyConfigDTO notify = new NotifyConfigDTO();
        notify.setId(Long.MAX_VALUE);
        for (Object value : new Object[] {resource, list, exception, notify}) {
            var id = mapper.readTree(mapper.writeValueAsString(value)).get("id");
            assertEquals(true, id.isTextual());
            assertEquals("9223372036854775807", id.asText());
        }
    }
}
