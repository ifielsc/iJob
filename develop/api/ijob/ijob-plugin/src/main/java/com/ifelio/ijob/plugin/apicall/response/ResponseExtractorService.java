package com.ifelio.ijob.plugin.apicall.response;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.apicall.ResponseExtractor;

import java.util.List;

public interface ResponseExtractorService {
    void extract(String body, List<ResponseExtractor> extractors, TaskContext context);
}
