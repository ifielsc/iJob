package com.ifelio.ijob.api.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.api.schedule.ScheduleController;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.auth.AccountRole;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.schedule.SchedulePreviewDTO;
import com.ifelio.ijob.contract.schedule.SchedulePreviewItemDTO;
import com.ifelio.ijob.contract.schedule.SchedulePreviewRequest;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

class ScheduleProjectionTest {
    @Test
    void everyRoleRetainsPublicPreviewWithoutBroadeningRecursiveProjection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var advice = new SafeReadProjection(mapper);
        var dto = new SchedulePreviewDTO();
        dto.setItems(
                List.of(new SchedulePreviewItemDTO(LocalDateTime.of(2026, 9, 28, 9, 0), false)));
        var response = ApiResponse.success(dto);
        var method =
                new MethodParameter(
                        ScheduleController.class.getMethod("preview", SchedulePreviewRequest.class),
                        -1);
        for (AccountRole role : AccountRole.values()) {
            var request = new MockHttpServletRequest();
            request.setAttribute(
                    ApiSecurityInterceptor.ACTOR,
                    new CurrentUserResponse(
                            "id",
                            "user",
                            "User",
                            role,
                            Set.of(AccountPermission.DEFINITION_READ),
                            false));
            assertSame(
                    response,
                    advice.beforeBodyWrite(
                            response,
                            method,
                            MediaType.APPLICATION_JSON,
                            MappingJackson2HttpMessageConverter.class,
                            new ServletServerHttpRequest(request),
                            new ServletServerHttpResponse(new MockHttpServletResponse())));
        }
        var arbitrary =
                mapper.readTree(
                        "{\"items\":[{\"fireTime\":\"secret\",\"calendarAllowed\":true}],\"variables\":{\"secret\":true}}");
        assertFalse(advice.project(arbitrary).toString().contains("secret"));
    }
}
