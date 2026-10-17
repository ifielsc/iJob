package com.ifelio.ijob.plugin.apicall;

import lombok.Data;

@Data
public class ResponseExtractor {
    /** TaskContext variable name to receive the extracted value. */
    private String variable;

    /** JSONPath expression, for example {@code $.data.token}. */
    private String expression;
}
