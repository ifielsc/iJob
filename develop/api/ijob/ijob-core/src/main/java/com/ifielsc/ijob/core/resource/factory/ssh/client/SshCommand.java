package com.ifielsc.ijob.core.resource.factory.ssh.client;

import java.io.InputStream;

public interface SshCommand extends AutoCloseable {
    InputStream stdout();

    InputStream stderr();

    boolean isFinished();

    int exitCode();

    @Override
    void close();
}
