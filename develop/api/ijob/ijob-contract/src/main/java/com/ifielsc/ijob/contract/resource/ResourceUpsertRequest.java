// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.resource;

import com.ifielsc.ijob.contract.enums.ResourceType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(
        exclude = {
            "dbConfig",
            "fileConfig",
            "serviceConfig",
            "rabbitConfig",
            "kafkaConfig",
            "monitorConfig",
            "sshConfig"
        })
public class ResourceUpsertRequest {
    private String code;
    private String name;
    private ResourceType type;
    private Boolean enabled;
    private String description;
    private ResourceConfigRequest.Db dbConfig;
    private ResourceConfigRequest.File fileConfig;
    private ResourceConfigRequest.Service serviceConfig;
    private ResourceConfigRequest.Rabbit rabbitConfig;
    private ResourceConfigRequest.Kafka kafkaConfig;
    private ResourceConfigRequest.Monitor monitorConfig;
    private ResourceConfigRequest.Ssh sshConfig;
}
