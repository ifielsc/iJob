package com.ifelio.ijob.common.response;

/** A stable message identity, independent of the transport status and the display locale. */
public interface MessageDefinition {
    String errorCode();

    String messageKey();

    String defaultMessage();
}
