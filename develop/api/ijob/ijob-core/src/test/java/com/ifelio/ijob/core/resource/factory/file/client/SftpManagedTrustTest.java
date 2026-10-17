package com.ifelio.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.resource.SshHostKeyProbeRequest;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.resource.factory.file.FileProtocol;
import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifelio.ijob.core.resource.factory.ssh.SshHostKeyProbeService;
import com.ifelio.ijob.core.resource.factory.ssh.SshResourceConfig;
import com.ifelio.ijob.core.resource.factory.ssh.client.JschSshClient;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

class SftpManagedTrustTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void managedAndFileTrustConnectButChangedIdentityNeverAuthenticates(boolean legacy)
            throws Exception {
        Path project = Path.of("").toAbsolutePath();
        while (!Files.exists(project.resolve("test/tools/stage7e-sftp-fixture.py"))) {
            project = project.getParent();
            if (project == null) throw new IllegalStateException("Fixture not found");
        }
        Process process =
                new ProcessBuilder(
                                System.getProperty(
                                        "fixture.python", "D:/ProgramData/anaconda3/python.exe"),
                                project.resolve("test/tools/stage7e-sftp-fixture.py").toString(),
                                legacy ? "--legacy" : "--modern")
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        try (var input = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = new java.io.PrintWriter(process.getOutputStream(), true)) {
            var fixture = JSONUtil.parseObj(input.readLine());
            int port = fixture.getInt("port");
            String key = fixture.getStr("key");
            var probe =
                    new SshHostKeyProbeService()
                            .probe(new SshHostKeyProbeRequest("127.0.0.1", port, 3000));
            assertEquals(key, probe.publicKey());
            output.println("count");
            assertEquals("0", input.readLine());
            var config = new FileResourceConfig();
            config.setProtocol(FileProtocol.SFTP);
            config.setHost("127.0.0.1");
            config.setPort(port);
            config.setUsername("fixture");
            config.setPassword("fixture-password");
            config.setBasePath("/");
            config.setTimeout(3000);
            config.setHostKeyVerification("MANAGED");
            config.setHostKey(key);
            try (var client = new SftpFileClient(config)) {
                assertEquals("report.csv", client.list("/").getFirst().getName());
                assertEquals("report.csv", client.list("").getFirst().getName());
            }
            output.println("count");
            assertEquals("1", input.readLine());
            byte[] changed = Base64.getDecoder().decode(key);
            changed[changed.length - 1] ^= 1;
            config.setHostKey(Base64.getEncoder().encodeToString(changed));
            var failure = assertThrows(BusinessException.class, () -> new SftpFileClient(config));
            assertEquals(CoreMessage.SSH_HOST_KEY_MISMATCH.errorCode(), failure.getErrorCode());
            output.println("count");
            assertEquals("1", input.readLine());
            Path known = directory.resolve("known_hosts");
            Files.writeString(
                    known, "[127.0.0.1]:" + port + " " + probe.keyType() + " " + key + "\n");
            config.setHostKeyVerification(null);
            config.setHostKey(null);
            config.setKnownHosts(known.toString());
            try (var client = new SftpFileClient(config)) {
                assertEquals(1, client.list("/").size());
            }
            output.println("count");
            assertEquals("2", input.readLine());
            var sshConfig = new SshResourceConfig();
            sshConfig.setHost("127.0.0.1");
            sshConfig.setPort(port);
            sshConfig.setUsername("fixture");
            sshConfig.setPassword("fixture-password");
            sshConfig.setConnectTimeout(3000);
            sshConfig.setHostKeyVerification("MANAGED");
            sshConfig.setHostKey(key);
            try (var ignored = new JschSshClient(sshConfig)) {
                output.println("count");
                assertEquals("3", input.readLine());
            }
            output.println("stop");
        } finally {
            process.destroy();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                process.destroyForcibly();
        }
    }
}
