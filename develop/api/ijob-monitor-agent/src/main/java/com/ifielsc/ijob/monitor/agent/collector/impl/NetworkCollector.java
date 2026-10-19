// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.impl;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.metric.NetworkMetric;
import com.ifielsc.ijob.monitor.agent.collector.MetricCollector;
import com.ifielsc.ijob.monitor.agent.collector.OshiCollectorSupport;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import oshi.hardware.NetworkIF;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class NetworkCollector implements MetricCollector<List<NetworkMetric>> {

    private final OshiCollectorSupport support;

    /** Previous counters and timestamps keyed by interface name. */
    private final Map<String, NetworkSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public CollectType type() {
        return CollectType.NETWORK;
    }

    @Override
    public synchronized List<NetworkMetric> collect() {
        List<NetworkMetric> list = new ArrayList<>();
        Set<String> activeNames = new HashSet<>();

        for (NetworkIF network : support.networkIFs()) {
            String name = network.getName();
            activeNames.add(name);
            NetworkSnapshot previous = snapshots.get(name);
            long now = System.currentTimeMillis();
            long recvRate = 0;
            long sendRate = 0;

            if (previous != null) {
                long interval = now - previous.getTimestamp();
                recvRate = rate(network.getBytesRecv(), previous.getReceiveBytes(), interval);
                sendRate = rate(network.getBytesSent(), previous.getSendBytes(), interval);
            }

            snapshots.put(
                    name, new NetworkSnapshot(network.getBytesRecv(), network.getBytesSent(), now));

            list.add(
                    NetworkMetric.builder()
                            .name(name)
                            .displayName(network.getDisplayName())
                            .mac(network.getMacaddr())
                            .receiveBytes(network.getBytesRecv())
                            .sendBytes(network.getBytesSent())
                            .receiveRate(recvRate)
                            .sendRate(sendRate)
                            .build());
        }

        snapshots.keySet().removeIf(name -> !activeNames.contains(name));
        return list;
    }

    static long rate(long current, long previous, long intervalMillis) {
        if (intervalMillis <= 0 || current < previous) {
            return 0L;
        }
        double bytesPerSecond = (current - previous) * 1000D / intervalMillis;
        return bytesPerSecond >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) bytesPerSecond;
    }
}
