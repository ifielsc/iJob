package com.ifielsc.ijob.common.response;

/** Stable, language-neutral error metadata exposed at system boundaries. */
public interface ErrorDefinition extends MessageDefinition {
    ResultCode resultCode();
}
