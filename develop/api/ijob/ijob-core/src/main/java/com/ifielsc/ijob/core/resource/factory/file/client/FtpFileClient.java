// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.resource.factory.file.FileResourceConfig;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Slf4j
public class FtpFileClient extends AbstractFileClient implements StagedFileClient {
    private final FTPClient ftpClient;

    FtpFileClient(FTPClient ftpClient, String rootPath) {
        super(rootPath);
        this.ftpClient = ftpClient;
    }

    public FtpFileClient(FileResourceConfig config) {
        super(config.getBasePath());

        FTPClient createdClient = new BoundedListingClient();
        try {
            createdClient.setConnectTimeout(config.getTimeout());
            createdClient.setDefaultTimeout(config.getTimeout());
            createdClient.setDataTimeout(java.time.Duration.ofMillis(config.getTimeout()));
            createdClient.connect(config.getHost(), config.getPort());

            boolean login = createdClient.login(config.getUsername(), config.getPassword());
            if (!login) {
                throw new IOException("FTP login failed");
            }
            createdClient.setControlEncoding(StandardCharsets.UTF_8.name());
            createdClient.setFileType(FTP.BINARY_FILE_TYPE);
            createdClient.enterLocalPassiveMode();
            createdClient.setKeepAlive(true);
            ftpClient = createdClient;
        } catch (IOException exception) {
            disconnectQuietly(createdClient);
            log.warn(
                    "FTP resource connection failed",
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.FTP_RESOURCE_CONNECTION_FAILED);
        }
    }

    @Override
    public List<FileInfo> list(String path) throws IOException {
        return list(path, 100_000);
    }

    @Override
    public List<FileInfo> list(String path, int maxEntries) throws IOException {
        String remote = resolve(path);

        FTPFile[] files =
                ftpClient instanceof BoundedListingClient bounded
                        ? bounded.listBounded(remote, maxEntries)
                        : ftpClient.listFiles(remote);
        if (files.length > maxEntries) throw new IOException("Directory entry quota exceeded");
        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            throw new FileClientException(
                    "FTP directory listing failed: " + remote,
                    FTPReply.isNegativeTransient(replyCode),
                    null);
        }
        List<FileInfo> result = new ArrayList<>();
        for (FTPFile file : files) {
            if (".".equals(file.getName()) || "..".equals(file.getName())) {
                continue;
            }
            result.add(
                    FileInfo.builder()
                            .name(file.getName())
                            .directory(file.isDirectory())
                            .size(file.getSize())
                            .lastModified(convertTime(file.getTimestamp()))
                            .build());
        }
        return result;
    }

    private LocalDateTime convertTime(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        return LocalDateTime.ofInstant(calendar.toInstant(), ZoneId.systemDefault());
    }

    /** Bound the wire listing before Commons Net materializes its parser input. */
    static class BoundedListingClient extends FTPClient {
        FTPFile[] listBounded(String remote, int maxEntries) throws IOException {
            // MLSD modification facts are UTC, whereas LIST dates use the server's
            // unspecified local timezone and can appear to be in the future.
            boolean machineListing = hasFeature("MLST");
            var parser =
                    machineListing
                            ? org.apache.commons.net.ftp.parser.MLSxEntryParser.getInstance()
                            : new org.apache.commons.net.ftp.parser
                                            .DefaultFTPFileEntryParserFactory()
                                    .createFileEntryParser(getSystemType());
            java.net.Socket socket =
                    _openDataConnection_(
                            machineListing
                                    ? org.apache.commons.net.ftp.FTPCmd.MLSD
                                    : org.apache.commons.net.ftp.FTPCmd.LIST,
                            remote);
            if (socket == null) return new FTPFile[0];
            List<String> lines = new ArrayList<>();
            try (socket;
                    InputStream input = socket.getInputStream()) {
                // Whole wire response capped even for malformed or unterminated listing lines.
                byte[] data = input.readNBytes(8 * 1024 * 1024 + 1);
                if (data.length > 8 * 1024 * 1024)
                    throw new IOException("Directory byte quota exceeded");
                try (var reader =
                        new java.io.BufferedReader(
                                new java.io.InputStreamReader(
                                        new java.io.ByteArrayInputStream(data),
                                        getControlEncoding()))) {
                    String entry;
                    while ((entry = parser.readNextEntry(reader)) != null) {
                        // Unix LIST may include one total/header line.
                        if (lines.size() >= maxEntries + 3)
                            throw new IOException("Directory entry quota exceeded");
                        lines.add(entry);
                    }
                }
            } catch (IOException exception) {
                disconnectQuietly(this);
                throw exception;
            }
            if (!completePendingCommand()) throw new IOException("FTP directory listing failed");
            List<FTPFile> files = new ArrayList<>();
            for (String line : parser.preParse(lines)) {
                FTPFile file = parser.parseFTPEntry(line);
                if (file != null) {
                    if (files.size() >= maxEntries)
                        throw new IOException("Directory entry quota exceeded");
                    files.add(file);
                }
            }
            return files.toArray(FTPFile[]::new);
        }
    }

    @Override
    public InputStream download(String path) throws IOException {
        String remote = resolve(path);

        InputStream input = ftpClient.retrieveFileStream(remote);
        if (input == null) {
            throw new FileNotFoundException(remote);
        }

        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                super.close();

                boolean completed = ftpClient.completePendingCommand();
                if (!completed) {
                    throw new IOException("FTP download incomplete");
                }
            }
        };
    }

    @Override
    public void upload(String path, InputStream input) throws IOException {
        String remote = resolve(path);

        String parent = parent(remote);
        if (parent != null) {
            mkdirs(parent);
        }
        boolean result = ftpClient.storeFile(remote, input);
        if (!result) {
            throw new IOException("FTP upload failed:" + remote);
        }
    }

    @Override
    public void renameFile(String from, String to) throws IOException {
        String source = resolve(from), target = resolve(to);
        boolean renamed;
        try {
            renamed = ftpClient.rename(source, target);
        } catch (IOException failure) {
            throw new FilePublicationUncertainException(failure);
        }
        if (!renamed) throw new IOException("FTP publication rejected");
    }

    @Override
    public void deleteTemporaryFile(String path) throws IOException {
        String remote = resolve(path);
        if (!StagedFileClient.isTemporaryName(fileName(remote)))
            throw new IOException("Invalid temporary file");
        if (!ftpClient.deleteFile(remote))
            throw new IOException("FTP temporary file cleanup unconfirmed");
    }

    @Override
    public void mkdirs(String path) throws IOException {
        String remote = resolve(path);

        String[] dirs = remote.split("/");
        String current = "";
        for (String dir : dirs) {
            if (dir.isBlank()) {
                continue;
            }

            current += "/" + dir;
            if (!isDirectory(current)) {
                if (exists(current)) {
                    throw new IOException("path is not a directory:" + current);
                }
                boolean ok = ftpClient.makeDirectory(current);
                if (!ok) {
                    throw new IOException("mkdir failed:" + current);
                }
            }
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        String remote = resolve(path);
        if (isDirectory(remote)) {
            return true;
        }
        FTPFile[] files = ftpClient.listFiles(remote);
        return files != null && files.length > 0;
    }

    @Override
    public void delete(String path) throws IOException {
        String remote = resolve(path);
        if (ftpClient.deleteFile(remote)) {
            return;
        }
        if (!isDirectory(remote)) {
            throw new IOException("FTP delete failed:" + remote);
        }
        deleteDirectory(remote);
    }

    private void deleteDirectory(String path) throws IOException {
        FTPFile[] files = ftpClient.listFiles(path);
        if (files == null) {
            throw new IOException("FTP directory listing failed:" + path);
        }
        for (FTPFile file : files) {
            if (file == null || ".".equals(file.getName()) || "..".equals(file.getName())) {
                continue;
            }
            String child = path + "/" + file.getName();
            if (file.isDirectory()) {
                deleteDirectory(child);
            } else {
                if (!ftpClient.deleteFile(child)) {
                    throw new IOException("FTP delete failed:" + child);
                }
            }
        }
        if (!ftpClient.removeDirectory(path)) {
            throw new IOException("FTP directory delete failed:" + path);
        }
    }

    private boolean isDirectory(String path) throws IOException {
        String original = ftpClient.printWorkingDirectory();
        boolean changed = ftpClient.changeWorkingDirectory(path);
        if (!changed) {
            return false;
        }
        if (original == null || !ftpClient.changeWorkingDirectory(original)) {
            throw new IOException("FTP working directory restore failed");
        }
        return true;
    }

    @Override
    public void close() {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (Exception exception) {
            log.warn(
                    "close ftp error",
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }

    private static void disconnectQuietly(FTPClient client) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception ignored) {
            // Constructor failure must not leave a half-created connection.
        }
    }
}
