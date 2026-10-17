package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifyPolicy;

import lombok.Data;

@Data
public class NotifyBindingUpdateRequest {
    private NotifyPolicy notifyPolicy;
}
