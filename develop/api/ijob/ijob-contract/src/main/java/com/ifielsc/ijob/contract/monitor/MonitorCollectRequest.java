// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.monitor;

import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorCollectRequest {
    @Builder.Default private List<CollectType> collectItems = new ArrayList<>();

    @Builder.Default private List<ApplicationCollectConfig> applications = new ArrayList<>();
}
