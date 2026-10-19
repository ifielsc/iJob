package com.ifielsc.ijob.infra.coordination.id;

@FunctionalInterface
public interface IdGenerator {
    long nextId(IdNamespace namespace);
}
