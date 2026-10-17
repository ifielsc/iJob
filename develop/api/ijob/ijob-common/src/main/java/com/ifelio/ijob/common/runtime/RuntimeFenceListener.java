package com.ifelio.ijob.common.runtime;

@FunctionalInterface
public interface RuntimeFenceListener {
    void onRuntimeFenced(String reason);
}
