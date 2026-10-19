package com.ifielsc.ijob.core.resource.factory.file.client;

import java.io.IOException;

/** A rename may have committed before its acknowledgement was lost. */
public final class FilePublicationUncertainException extends IOException {
    private static final long serialVersionUID = 1L;

    public FilePublicationUncertainException(Throwable cause) {
        super("File publication result is uncertain", cause);
    }
}
