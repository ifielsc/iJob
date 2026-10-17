package com.ifelio.ijob.contract.job;

import com.ifelio.ijob.contract.enums.GatewayMode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobGatewayResponse {
    private GatewayMode mode;
}
