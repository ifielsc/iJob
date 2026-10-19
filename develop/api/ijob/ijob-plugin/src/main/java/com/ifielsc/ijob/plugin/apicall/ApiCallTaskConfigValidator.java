package com.ifielsc.ijob.plugin.apicall;

import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.WebServiceRequest;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ApiCallTaskConfigValidator {
    private static final int MAX_PATH_LENGTH = 2048;
    private static final int MAX_HEADER_COUNT = 100;
    private static final int MAX_HEADER_NAME_LENGTH = 256;
    private static final int MAX_HEADER_VALUE_LENGTH = 8192;
    private static final int MAX_EXTRACTOR_COUNT = 64;
    private static final int MAX_VARIABLE_LENGTH = 128;
    private static final int MAX_EXPRESSION_LENGTH = 1024;
    private static final int MIN_RESPONSE_BYTES = 1024;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final String XML_NAME = "[A-Za-z_][A-Za-z0-9_.-]*";
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "GT", "LT");

    public void validate(ApiCallConfig config) {
        if (config == null) {
            throw invalid("API call configuration is required");
        }
        if (config.getResourceId() == null || config.getResourceId() <= 0) {
            throw invalid("API call resourceId must be positive");
        }
        validatePath(config.getPath());
        validateMethod(config.getMethod());
        validateHeaders(config.getHeaders());
        validateResponseLimit(config.getMaxResponseBytes());
        validateExtractors(config.getExtractors());
        validateSuccessCondition(config.getSuccessCondition());
        validateWebService(config.getWebService());
        RemoteStopConfig stop = config.getRemoteStop();
        if (stop != null) {
            if (stop.getTimeoutSeconds() == null
                    || stop.getTimeoutSeconds() < 1
                    || stop.getTimeoutSeconds() > 30)
                throw invalid("Remote stop timeout must be between 1 and 30 seconds");
            if ((config.getWebService() == null) != (stop.getWebService() == null))
                throw invalid("Remote stop protocol must match the main request");
            validateMethod(stop.getMethod());
            validatePath(stop.getPath());
            validateHeaders(stop.getHeaders());
            validateWebService(stop.getWebService());
            if (stop.getWebService() == null
                    && (stop.getPath() == null || stop.getPath().isBlank()))
                throw invalid("Remote stop path is required");
        }
    }

    public void validateResolved(ServiceRequest request) {
        if (request == null) {
            throw invalid("resolved API call request is required");
        }
        validateResolvedPath(request.getPath());
        validateHeaders(request.getHeaders());
        validateResolvedWebService(request.getWebService());
    }

    private void validatePath(String path) {
        if (path != null && (path.length() > MAX_PATH_LENGTH || containsLineBreak(path))) {
            throw invalid("API call path is invalid");
        }
    }

    private void validateMethod(String method) {
        if (method == null || !METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            throw invalid("API call method is unsupported");
        }
    }

    private void validateResponseLimit(Integer maxResponseBytes) {
        if (maxResponseBytes == null
                || maxResponseBytes < MIN_RESPONSE_BYTES
                || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw invalid("API call maxResponseBytes must be between 1024 and 16777216");
        }
    }

    private void validateResolvedPath(String path) {
        validatePath(path);
        if (path == null) {
            return;
        }
        if (path.chars()
                        .anyMatch(
                                character ->
                                        Character.isWhitespace(character)
                                                || Character.isISOControl(character))
                || path.startsWith("//")
                || path.contains("://")) {
            throw invalid("resolved API call path is invalid");
        }
    }

    private void validateHeaders(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        if (headers.size() > MAX_HEADER_COUNT) {
            throw invalid("API call has too many headers");
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null
                    || name.isBlank()
                    || name.length() > MAX_HEADER_NAME_LENGTH
                    || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || containsUnsafeControl(name)
                    || value == null
                    || value.length() > MAX_HEADER_VALUE_LENGTH
                    || containsUnsafeControl(value)) {
                throw invalid("API call header is invalid");
            }
        }
    }

    private void validateExtractors(List<ResponseExtractor> extractors) {
        if (extractors == null) {
            return;
        }
        if (extractors.size() > MAX_EXTRACTOR_COUNT) {
            throw invalid("API call has too many response extractors");
        }
        Set<String> variables = new HashSet<>();
        for (ResponseExtractor extractor : extractors) {
            if (extractor == null
                    || extractor.getVariable() == null
                    || extractor.getVariable().isBlank()
                    || extractor.getVariable().length() > MAX_VARIABLE_LENGTH
                    || "response".equals(extractor.getVariable())
                    || !variables.add(extractor.getVariable())
                    || extractor.getExpression() == null
                    || extractor.getExpression().isBlank()
                    || extractor.getExpression().length() > MAX_EXPRESSION_LENGTH) {
                throw invalid("API call response extractor is invalid");
            }
            validateJsonPath(extractor.getExpression());
        }
    }

    private void validateSuccessCondition(SuccessCondition condition) {
        if (condition == null) {
            return;
        }
        if (condition.getExpression() == null
                || condition.getExpression().isBlank()
                || condition.getExpression().length() > MAX_EXPRESSION_LENGTH
                || condition.getOperator() == null
                || !OPERATORS.contains(condition.getOperator().toUpperCase(Locale.ROOT))
                || condition.getExpected() == null
                || condition.getExpected().length() > MAX_HEADER_VALUE_LENGTH) {
            throw invalid("API call success condition is invalid");
        }
        validateJsonPath(condition.getExpression());
    }

    private void validateJsonPath(String expression) {
        // Jayway accepts a trailing '[' and silently evaluates the preceding path.
        // Check bracket completeness before compiling, including quoted keys and regexes.
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        boolean regex = false;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && (quote != 0 || regex)) {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) quote = 0;
                continue;
            }
            if (regex) {
                if (current == '/') regex = false;
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '/') {
                regex = true;
                continue;
            }
            if (current == '[') depth++;
            if (current == ']' && --depth < 0) throw invalid("API call JSONPath is invalid");
        }
        if (depth != 0 || quote != 0 || regex || escaped)
            throw invalid("API call JSONPath is invalid");
        try {
            com.jayway.jsonpath.JsonPath.compile(expression);
        } catch (RuntimeException exception) {
            throw invalid("API call JSONPath is invalid");
        }
    }

    private void validateWebService(WebServiceCallConfig webService) {
        if (webService == null) {
            return;
        }
        if (webService.getOperation() == null
                || webService.getOperation().isBlank()
                || webService.getOperation().length() > MAX_VARIABLE_LENGTH
                || webService.getAction() != null
                        && (webService.getAction().length() > MAX_PATH_LENGTH
                                || containsLineBreak(webService.getAction()))) {
            throw invalid("API call WebService configuration is invalid");
        }
        Map<String, Object> parameters = webService.getParameters();
        if (parameters == null) {
            return;
        }
        if (parameters.size() > MAX_HEADER_COUNT
                || parameters.keySet().stream()
                        .anyMatch(
                                name ->
                                        name == null
                                                || !name.matches("[A-Za-z_][A-Za-z0-9_.-]*"))) {
            throw invalid("API call WebService parameters are invalid");
        }
    }

    private void validateResolvedWebService(WebServiceRequest webService) {
        if (webService == null) {
            return;
        }
        String operation = webService.getOperation();
        String action = webService.getAction();
        if (operation == null
                || !operation.matches(XML_NAME)
                || action != null
                        && (action.length() > MAX_PATH_LENGTH || containsUnsafeControl(action))) {
            throw invalid("resolved API call WebService configuration is invalid");
        }
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private boolean containsUnsafeControl(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl(character));
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
