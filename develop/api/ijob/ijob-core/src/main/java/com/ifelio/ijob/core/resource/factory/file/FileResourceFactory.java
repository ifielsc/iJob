package com.ifelio.ijob.core.resource.factory.file;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.core.resource.factory.ResourceClientFactory;
import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.core.resource.factory.file.client.FtpFileClient;
import com.ifelio.ijob.core.resource.factory.file.client.LocalFileClient;
import com.ifelio.ijob.core.resource.factory.file.client.S3CompatibleFileClient;
import com.ifelio.ijob.core.resource.factory.file.client.SftpFileClient;
import com.ifelio.ijob.infra.entity.resource.Resource;

import org.springframework.stereotype.Component;

@Component
public class FileResourceFactory implements ResourceClientFactory {
    @Override
    public ResourceType supportType() {
        return ResourceType.FILE;
    }

    @Override
    public Object create(Resource resource) {
        FileResourceConfig config = JSONUtil.toBean(resource.getConfig(), FileResourceConfig.class);

        return switch (config.getProtocol()) {
            case LOCAL -> new LocalFileClient(config);
            case FTP -> new FtpFileClient(config);
            case SFTP -> new SftpFileClient(config);
            case S3_COMPATIBLE -> new S3CompatibleFileClient(config);
        };
    }

    @Override
    public void verifyConnection(Object client) throws Exception {
        ((FileClient) client).list("");
    }
}
