package com.ifelio.ijob.core.resource.factory.mq.rabbit;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = {"host", "username", "password", "virtualHost", "exchange", "routingKey"})
public class RabbitResourceConfig {
    private String host;

    private Integer port = 5672;

    private String username;

    private String password;

    private String virtualHost = "/";

    /** exchange */
    private String exchange;

    /** routingKey */
    private String routingKey;

    private boolean publisherConfirm = true;
}
