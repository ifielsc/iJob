package com.ifelio.ijob.contract.resource;

import com.ifelio.ijob.contract.enums.ResourceType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResourceListDTO {
    private Long id;
    private String code;
    private String name;
    private ResourceType type;
    private Boolean enabled;
    private String description;
    private LocalDateTime updateTime;
}
