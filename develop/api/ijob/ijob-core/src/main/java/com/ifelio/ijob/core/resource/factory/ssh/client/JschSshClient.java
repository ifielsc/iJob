package com.ifelio.ijob.core.resource.factory.ssh.client;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.resource.factory.ssh.SshHostKeys;
import com.ifelio.ijob.core.resource.factory.ssh.SshResourceConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;

public class JschSshClient implements SshClient {
    private final Session session;
    private final int connectTimeout;

    public JschSshClient(SshResourceConfig config) {
        Session created = null;
        try {
            JSch jsch = new JSch();
            SshHostKeys.configure(
                    jsch,
                    SshHostKeys.mode(config),
                    config.getHost(),
                    config.getPort(),
                    config.getHostKey(),
                    config.getKnownHosts());
            if (hasText(config.getPrivateKey())) {
                if (hasText(config.getPassphrase())) {
                    jsch.addIdentity(config.getPrivateKey(), config.getPassphrase());
                } else {
                    jsch.addIdentity(config.getPrivateKey());
                }
            }
            created = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            SshHostKeys.configureAlgorithms(created);
            if (hasText(config.getPassword())) {
                created.setPassword(config.getPassword());
            }
            created.setConfig("StrictHostKeyChecking", "yes");
            created.connect(config.getConnectTimeout());
            session = created;
            connectTimeout = config.getConnectTimeout();
        } catch (Exception exception) {
            if (created != null) {
                created.disconnect();
            }
            if (exception instanceof com.jcraft.jsch.JSchChangedHostKeyException
                    || exception instanceof com.jcraft.jsch.JSchUnknownHostKeyException) {
                throw new BusinessException(ResultCode.CONFLICT, CoreMessage.SSH_HOST_KEY_MISMATCH);
            }
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.SSH_RESOURCE_CONNECTION_FAILED);
        }
    }

    @Override
    public SshCommand openCommand(String command, InputStream standardInput) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setPty(false);
            channel.setCommand(command);
            channel.setInputStream(standardInput, false);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(connectTimeout);
            return new JschSshCommand(channel, stdout, stderr);
        } catch (Exception exception) {
            if (channel != null) {
                channel.disconnect();
            }
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.SSH_COMMAND_STARTUP_FAILED);
        }
    }

    @Override
    public void close() {
        session.disconnect();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record JschSshCommand(ChannelExec channel, InputStream stdout, InputStream stderr)
            implements SshCommand {
        @Override
        public boolean isFinished() {
            return channel.isClosed();
        }

        @Override
        public int exitCode() {
            if (!isFinished()) {
                throw new IllegalStateException("SSH command is still running");
            }
            return channel.getExitStatus();
        }

        @Override
        public void close() {
            channel.disconnect();
        }
    }
}
