package com.ifelio.ijob.plugin.push.compress;

import com.ifelio.ijob.plugin.push.publisher.SerializedPayload;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class GzipCompressionService implements CompressionService {
    @Override
    public String name() {
        return "gzip";
    }

    @Override
    public boolean support(SerializedPayload payload) {
        return true;
    }

    @Override
    public SerializedPayload compress(SerializedPayload payload) throws Exception {
        byte[] compressed = gzip(payload.getData());
        return SerializedPayload.builder()
                .data(compressed)
                .contentType(payload.getContentType())
                .contentEncoding("gzip")
                .originalSize(payload.getOriginalSize())
                .serializedSize(compressed.length)
                .build();
    }

    private byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }
}
