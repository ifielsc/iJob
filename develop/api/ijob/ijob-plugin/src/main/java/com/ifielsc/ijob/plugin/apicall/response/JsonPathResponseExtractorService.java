// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall.response;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.apicall.ResponseExtractor;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JsonPathResponseExtractorService implements ResponseExtractorService {
    @Override
    public void extract(String body, List<ResponseExtractor> extractors, TaskContext context) {
        if (CollUtil.isEmpty(extractors)) {
            return;
        }
        if (StrUtil.isBlank(body)) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "response extract failed: empty body");
        }

        DocumentContext document = JsonPath.parse(body);
        Map<String, Object> values = new LinkedHashMap<>();
        for (ResponseExtractor extractor : extractors) {
            Object value;
            try {
                value = document.read(extractor.getExpression());
            } catch (Exception e) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR,
                        "response extract failed:" + extractor.getExpression());
            }
            values.put(extractor.getVariable(), value);
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                context.removeVariable(entry.getKey());
            } else {
                context.putVariable(entry.getKey(), entry.getValue());
            }
        }
    }
}
