package com.ifielsc.ijob.core.concurrency;

import com.ifielsc.ijob.contract.enums.PluginType;

public final class ConcurrencyScopes {
    private ConcurrencyScopes() {}

    public static String plugin(PluginType pluginType) {
        return "PLUGIN:" + pluginType.name();
    }

    public static String task(long taskId) {
        return "TASK:" + taskId;
    }

    public static String resource(long resourceId) {
        return "RESOURCE:" + resourceId;
    }
}
