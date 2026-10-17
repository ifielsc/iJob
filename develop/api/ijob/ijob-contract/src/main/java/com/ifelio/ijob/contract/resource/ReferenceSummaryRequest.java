package com.ifelio.ijob.contract.resource;

import java.util.List;

public record ReferenceSummaryRequest(List<Long> resourceIds, List<Long> notificationIds) {}
