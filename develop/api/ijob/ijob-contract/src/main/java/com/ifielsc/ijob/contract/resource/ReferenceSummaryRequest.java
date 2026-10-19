package com.ifielsc.ijob.contract.resource;

import java.util.List;

public record ReferenceSummaryRequest(List<Long> resourceIds, List<Long> notificationIds) {}
