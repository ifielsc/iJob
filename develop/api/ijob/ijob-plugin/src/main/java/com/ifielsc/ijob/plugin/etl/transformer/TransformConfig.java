package com.ifielsc.ijob.plugin.etl.transformer;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class TransformConfig {
    private TransformType type;

    private Map<String, Object> properties = new HashMap<>();
}
