// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.resource;

import java.util.List;

public record ReferenceSummaryRequest(List<Long> resourceIds, List<Long> notificationIds) {}
