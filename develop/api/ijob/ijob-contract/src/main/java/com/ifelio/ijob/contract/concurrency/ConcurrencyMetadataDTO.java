package com.ifelio.ijob.contract.concurrency;

import java.util.List;

public record ConcurrencyMetadataDTO(
        List<PluginActivityDTO> plugins,
        int minConcurrency,
        int maxConcurrency,
        long permitWaitTimeoutMillis,
        long retryIntervalMillis,
        long maxRetryIntervalMillis,
        long retryJitterMillis,
        String configurationSource) {}
