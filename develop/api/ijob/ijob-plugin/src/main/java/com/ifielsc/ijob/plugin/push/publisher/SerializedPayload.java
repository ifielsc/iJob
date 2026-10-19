package com.ifielsc.ijob.plugin.push.publisher;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SerializedPayload {
    private byte[] data;

    private String contentType;

    /** Payload size in bytes before compression. */
    private int originalSize;

    /** Current payload size in bytes. */
    private int serializedSize;

    /** Content encoding, such as gzip; null means no encoding. */
    private String contentEncoding;
}
