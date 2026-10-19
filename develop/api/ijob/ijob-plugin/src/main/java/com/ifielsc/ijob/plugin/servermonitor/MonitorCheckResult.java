package com.ifielsc.ijob.plugin.servermonitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorCheckResult {
    private LocalDateTime collectTime;

    @Builder.Default private List<MonitorCheckItem> items = new ArrayList<>();

    public void add(MonitorCheckItem item) {
        items.add(item);
    }

    public boolean hasAlarm() {
        return items.stream().anyMatch(item -> item.getLevel() != AlarmLevel.NORMAL);
    }

    public boolean hasCritical() {
        return items.stream().anyMatch(item -> item.getLevel() == AlarmLevel.CRITICAL);
    }

    public int count(AlarmLevel level) {
        return (int) items.stream().filter(item -> item.getLevel() == level).count();
    }

    public int alarmCount() {
        return (int) items.stream().filter(item -> item.getLevel() != AlarmLevel.NORMAL).count();
    }

    public AlarmLevel maxLevel() {
        if (hasCritical()) {
            return AlarmLevel.CRITICAL;
        }
        if (hasAlarm()) {
            return AlarmLevel.WARNING;
        }
        return AlarmLevel.NORMAL;
    }
}
