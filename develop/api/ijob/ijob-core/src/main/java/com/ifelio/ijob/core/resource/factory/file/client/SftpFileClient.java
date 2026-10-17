package com.ifelio.ijob.core.resource.factory.file.client;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifelio.ijob.core.resource.factory.ssh.SshHostKeys;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import io.micrometer.common.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

@Slf4j
public class SftpFileClient extends AbstractFileClient implements StagedFileClient {
    private final Session session;
    private final ChannelSftp sftp;

    SftpFileClient(ChannelSftp sftp, String rootPath) {
        super(rootPath);
        this.sftp = sftp;
        this.session = null;
    }

    public SftpFileClient(FileResourceConfig config) {
        super(config.getBasePath());

        Session createdSession = null;
        ChannelSftp createdSftp = null;
        try {
            JSch jsch = new JSch();
            SshHostKeys.configure(
                    jsch,
                    config.getHostKeyVerification(),
                    config.getHost(),
                    config.getPort(),
                    config.getHostKey(),
                    config.getKnownHosts());
            if (StringUtils.isNotBlank(config.getPrivateKey())) {
                if (StringUtils.isBlank(config.getPassphrase())) {
                    jsch.addIdentity(config.getPrivateKey());
                } else {
                    jsch.addIdentity(config.getPrivateKey(), config.getPassphrase());
                }
            }

            createdSession =
                    jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            SshHostKeys.configureAlgorithms(createdSession);
            if (StringUtils.isNotBlank(config.getPassword())) {
                createdSession.setPassword(config.getPassword());
            }
            createdSession.setConfig("StrictHostKeyChecking", "yes");
            createdSession.connect(config.getTimeout());
            createdSftp = (ChannelSftp) createdSession.openChannel("sftp");
            createdSftp.connect(config.getTimeout());
            session = createdSession;
            sftp = createdSftp;
        } catch (Exception exception) {
            disconnectQuietly(createdSftp, createdSession);
            if (exception instanceof com.jcraft.jsch.JSchChangedHostKeyException
                    || exception instanceof com.jcraft.jsch.JSchUnknownHostKeyException)
                throw new BusinessException(ResultCode.CONFLICT, CoreMessage.SSH_HOST_KEY_MISMATCH);
            log.warn(
                    "SFTP resource connection failed",
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.SFTP_RESOURCE_CONNECTION_FAILED);
        }
    }

    @Override
    public List<FileInfo> list(String path) throws IOException {
        return list(path, 100_000);
    }

    @Override
    public List<FileInfo> list(String path, int maxEntries) throws IOException {
        try {
            Vector<ChannelSftp.LsEntry> entries = new Vector<>();
            sftp.ls(
                    resolveChecked(path),
                    entry -> {
                        entries.add(entry);
                        return entries.size() > maxEntries
                                ? ChannelSftp.LsEntrySelector.BREAK
                                : ChannelSftp.LsEntrySelector.CONTINUE;
                    });
            if (entries.size() > maxEntries)
                throw new IOException("Directory entry quota exceeded");
            List<FileInfo> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }

                SftpATTRS attrs = entry.getAttrs();
                rejectLink(attrs);
                result.add(
                        FileInfo.builder()
                                .name(name)
                                .directory(attrs.isDir())
                                .size(attrs.getSize())
                                .lastModified(
                                        LocalDateTime.ofInstant(
                                                Instant.ofEpochSecond(attrs.getMTime()),
                                                ZoneId.systemDefault()))
                                .build());
            }
            return result;
        } catch (SftpException e) {
            boolean retryable =
                    e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE
                            && e.id != ChannelSftp.SSH_FX_PERMISSION_DENIED;
            throw new FileClientException("SFTP directory listing failed", retryable, e);
        }
    }

    @Override
    public InputStream download(String path) throws IOException {
        try {
            return new BufferedInputStream(sftp.get(resolveChecked(path)));
        } catch (SftpException e) {
            throw new FileNotFoundException(path);
        }
    }

    @Override
    public void upload(String path, InputStream input) throws IOException {
        try {
            String remote = resolveChecked(path);
            mkdirs(parent(remote));
            resolveChecked(remote);
            sftp.put(input, remote);
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void renameFile(String from, String to) throws IOException {
        try {
            String source = resolveChecked(from), target = resolveChecked(to);
            sftp.rename(source, target);
        } catch (SftpException failure) {
            if (failure.id == ChannelSftp.SSH_FX_CONNECTION_LOST
                    || failure.id == ChannelSftp.SSH_FX_NO_CONNECTION
                    || failure.getCause() instanceof IOException)
                throw new FilePublicationUncertainException(failure);
            throw new IOException("SFTP publication rejected", failure);
        }
    }

    @Override
    public void deleteTemporaryFile(String path) throws IOException {
        try {
            String remote = resolveChecked(path);
            if (!StagedFileClient.isTemporaryName(fileName(remote)))
                throw new IOException("Invalid temporary file");
            sftp.rm(remote);
        } catch (SftpException failure) {
            if (failure.id != ChannelSftp.SSH_FX_NO_SUCH_FILE)
                throw new IOException("SFTP temporary file cleanup failed", failure);
        }
    }

    @Override
    public void mkdirs(String path) throws IOException {
        try {
            String remote = resolveChecked(path);
            String[] dirs = remote.split("/");
            String current = "";
            for (String dir : dirs) {
                if (dir.isBlank()) {
                    continue;
                }

                current += "/" + dir;
                try {
                    SftpATTRS attrs = sftp.lstat(current);
                    rejectLink(attrs);
                    if (!attrs.isDir()) throw new IOException("Not a directory");
                } catch (SftpException e) {
                    if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e;
                    sftp.mkdir(current);
                }
            }
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        try {
            sftp.lstat(resolveChecked(path));
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
            throw new IOException("SFTP existence check failed", e);
        }
    }

    @Override
    public void delete(String path) throws IOException {
        try {
            String remote = resolveChecked(path);
            SftpATTRS attrs = sftp.lstat(remote);
            if (attrs.isDir()) {
                deleteDirectory(remote);
            } else {
                sftp.rm(remote);
            }
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    private void deleteDirectory(String path) throws SftpException {
        Vector<ChannelSftp.LsEntry> files = sftp.ls(path);
        for (ChannelSftp.LsEntry entry : files) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            String child = buildPath(path, name);
            rejectLink(entry.getAttrs());
            if (entry.getAttrs().isDir()) {
                deleteDirectory(child);
            } else {
                sftp.rm(child);
            }
        }
        sftp.rmdir(path);
    }

    private String resolveChecked(String path) throws SftpException {
        String remote = resolve(path);
        String current = "";
        for (String segment : remote.split("/")) {
            if (segment.isBlank()) continue;
            current += "/" + segment;
            try {
                rejectLink(sftp.lstat(current));
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) break;
                throw e;
            }
        }
        return remote;
    }

    private static void rejectLink(SftpATTRS attrs) {
        if (attrs.isLink()) throw new SecurityException("Symbolic links are not allowed");
    }

    @Override
    public void close() {
        try {
            if (sftp != null) {
                sftp.disconnect();
            }
        } catch (Exception exception) {
            log.warn(
                    "close sftp error",
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }

        try {
            if (session != null) {
                session.disconnect();
            }
        } catch (Exception exception) {
            log.warn(
                    "close session error",
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }

    private static void disconnectQuietly(ChannelSftp channel, Session createdSession) {
        try {
            if (channel != null) {
                channel.disconnect();
            }
        } catch (Exception ignored) {
            // Continue with session cleanup.
        }
        try {
            if (createdSession != null) {
                createdSession.disconnect();
            }
        } catch (Exception ignored) {
            // Constructor failure must not leave a half-created session.
        }
    }
}
