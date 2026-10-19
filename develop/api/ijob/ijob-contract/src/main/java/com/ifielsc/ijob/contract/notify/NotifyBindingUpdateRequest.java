package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyPolicy;

import lombok.Data;

@Data
public class NotifyBindingUpdateRequest {
    private NotifyPolicy notifyPolicy;
}
