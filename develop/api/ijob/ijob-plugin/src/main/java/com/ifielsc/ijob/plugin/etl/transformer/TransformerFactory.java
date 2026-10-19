package com.ifielsc.ijob.plugin.etl.transformer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TransformerFactory {
    private final Map<String, Transformer> transformers;

    public TransformerFactory(List<Transformer> list) {
        transformers = list.stream().collect(Collectors.toMap(Transformer::type, t -> t));
    }

    public Transformer get(TransformType type) {
        Transformer transformer = transformers.get(type.name());

        if (transformer == null) {
            throw new IllegalArgumentException("Unsupported transformer:" + type);
        }

        return transformer;
    }
}
