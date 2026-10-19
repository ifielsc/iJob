// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall.response;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.apicall.ResponseExtractor;

import java.util.List;

public interface ResponseExtractorService {
    void extract(String body, List<ResponseExtractor> extractors, TaskContext context);
}
