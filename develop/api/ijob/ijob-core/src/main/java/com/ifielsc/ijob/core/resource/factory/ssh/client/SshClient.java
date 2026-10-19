// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.ssh.client;

import java.io.InputStream;

public interface SshClient extends AutoCloseable {
    SshCommand openCommand(String command, InputStream standardInput);

    @Override
    default void close() {}
}
