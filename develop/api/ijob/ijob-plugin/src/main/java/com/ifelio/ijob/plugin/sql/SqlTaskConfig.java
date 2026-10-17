package com.ifelio.ijob.plugin.sql;

import lombok.Data;

@Data
public class SqlTaskConfig {
    private Long resourceId;
    private String script;
    private String delimiter = ";";
    private boolean transaction = true;
    private boolean continueOnError = false;
    private Integer queryTimeoutSeconds = 600;
    private Integer minRows;
    private Integer maxRows;
    private String resultVariable;
}
