package com.ifelio.ijob.infra.coordination.id;

@FunctionalInterface
public interface IdGenerator {
    long nextId(IdNamespace namespace);
}
