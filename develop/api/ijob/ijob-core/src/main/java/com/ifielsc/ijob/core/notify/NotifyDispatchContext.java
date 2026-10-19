package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class NotifyDispatchContext {
    @ToString.Include private final NotifySendSource sourceType;

    private final String correlationId;
    private final String operatorId;

    @ToString.Include private final TargetType targetType;

    @ToString.Include private final Long targetId;

    @ToString.Include private final Long instanceId;

    @ToString.Include private final Long notifyBindingId;

    public static NotifyDispatchContext system() {
        return builder().sourceType(NotifySendSource.SYSTEM).build();
    }
}
