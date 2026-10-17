package com.ifelio.ijob.core.resource.factory.ssh;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.core.resource.factory.ResourceClientFactory;
import com.ifelio.ijob.core.resource.factory.ssh.client.JschSshClient;
import com.ifelio.ijob.core.resource.factory.ssh.client.SshClient;
import com.ifelio.ijob.infra.entity.resource.Resource;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class SshResourceFactory implements ResourceClientFactory {
    @Override
    public ResourceType supportType() {
        return ResourceType.SSH;
    }

    @Override
    public Object create(Resource resource) {
        return new JschSshClient(JSONUtil.toBean(resource.getConfig(), SshResourceConfig.class));
    }

    @Override
    public void verifyConnection(Object client) {
        try (var command =
                ((SshClient) client).openCommand("true", new ByteArrayInputStream(new byte[0]))) {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!command.isFinished() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            if (!command.isFinished() || command.exitCode() != 0) {
                throw new IllegalStateException("SSH command test failed");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SSH command test interrupted", exception);
        }
    }
}
