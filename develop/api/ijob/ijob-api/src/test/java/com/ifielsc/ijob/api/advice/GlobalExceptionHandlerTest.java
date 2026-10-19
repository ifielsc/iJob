package com.ifielsc.ijob.api.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.ResultCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Locale;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void useEnglishByDefault() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @Test
    void businessExceptionShouldPreserveResultCodeAndHttpStatus() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(
                        new BusinessException(
                                ResultCode.SERVICE_UNAVAILABLE,
                                "资源连接失败",
                                new IllegalStateException("internal connection details")));

        assertError(
                response, HttpStatus.SERVICE_UNAVAILABLE, ResultCode.SERVICE_UNAVAILABLE, "资源连接失败");
    }

    @Test
    void notLoginExceptionShouldReturnUnauthorized() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotLoginException();

        assertError(
                response,
                HttpStatus.UNAUTHORIZED,
                ResultCode.UNAUTHORIZED,
                ResultCode.UNAUTHORIZED.message());
    }

    @Test
    void permissionExceptionShouldReturnForbidden() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleForbiddenException();

        assertError(
                response,
                HttpStatus.FORBIDDEN,
                ResultCode.FORBIDDEN,
                ResultCode.FORBIDDEN.message());
    }

    @Test
    void bodyValidationExceptionShouldReturnFirstFieldError() throws NoSuchMethodException {
        Request request = new Request();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.addError(new FieldError("request", "name", "不能为空"));
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("accept", Request.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodArgumentNotValidException(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR, "name: 不能为空");
    }

    @Test
    void bindingExceptionShouldReturnBadRequest() {
        BindException exception = new BindException(new Request(), "request");
        exception.addError(new FieldError("request", "page", "必须是整数"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR, "page: 必须是整数");
    }

    @Test
    void executionStopShouldNotBeSwallowedByUnknownExceptionHandling() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleStopException(new StopException());

        assertError(
                response, HttpStatus.CONFLICT, ResultCode.CONFLICT, "Execution has been stopped");
        assertEquals("IJOB-RUNTIME-0409", response.getBody().getErrorCode());
        assertEquals("error.execution.stopped", response.getBody().getMessageKey());
    }

    @Test
    void acceptLanguageShouldResolveChineseMessage() {
        Locale previous = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleStopException(new StopException());

            assertEquals("执行已停止", response.getBody().getMessage());
            assertEquals("IJOB-RUNTIME-0409", response.getBody().getErrorCode());
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    @Test
    void unexpectedExceptionShouldHideInternalDetails() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpectedException(
                        new IllegalStateException("internal implementation details"));

        assertError(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ResultCode.SYSTEM_ERROR,
                ResultCode.SYSTEM_ERROR.message());
    }

    private static void accept(Request request) {}

    private void assertError(
            ResponseEntity<ApiResponse<Void>> response,
            HttpStatus expectedStatus,
            ResultCode expectedCode,
            String expectedMessage) {
        ApiResponse<Void> body = response.getBody();

        assertEquals(expectedStatus, response.getStatusCode());
        assertFalse(body.isSuccess());
        assertEquals(expectedCode.code(), body.getCode());
        assertEquals(expectedMessage, body.getMessage());
        assertNull(body.getData());
    }

    private static final class Request {
        private String name;
        private Integer page;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }
    }
}
