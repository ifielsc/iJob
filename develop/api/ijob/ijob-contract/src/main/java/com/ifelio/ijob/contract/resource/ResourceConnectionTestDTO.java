package com.ifelio.ijob.contract.resource;

import com.ifelio.ijob.contract.enums.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceConnectionTestDTO {
    private Long resourceId;
    private ResourceType type;
    private boolean successful;
}
