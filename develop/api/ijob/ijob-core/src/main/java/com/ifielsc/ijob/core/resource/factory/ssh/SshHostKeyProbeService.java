// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.ssh;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.resource.SshHostKeyDTO;
import com.ifielsc.ijob.contract.resource.SshHostKeyProbeRequest;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.JSchUnknownHostKeyException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class SshHostKeyProbeService {
    public SshHostKeyDTO probe(SshHostKeyProbeRequest request) {
        if (request == null || !SshHostKeys.validHost(request.host()))
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.SSH_HOST_KEY_INVALID);
        String host = request.host().trim();
        int port = request.port() == null ? 22 : request.port();
        int timeout = request.connectTimeout() == null ? 10000 : request.connectTimeout();
        if (port < 1 || port > 65535 || timeout < 100 || timeout > 300000)
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.SSH_HOST_KEY_INVALID);
        Capture repository = new Capture();
        Session session = null;
        try {
            JSch jsch = new JSch();
            jsch.setHostKeyRepository(repository);
            session = jsch.getSession("ijob-host-probe", host, port);
            SshHostKeys.configureAlgorithms(session);
            session.setConfig("StrictHostKeyChecking", "yes");
            // checkHost runs after the signed key exchange and before any user authentication.
            // Rejecting every key intentionally ends the connection without sending credentials.
            session.connect(Math.min(timeout, 10000));
        } catch (JSchUnknownHostKeyException exception) {
            if (repository.observed != null) {
                String key = Base64.getEncoder().encodeToString(repository.observed);
                return new SshHostKeyDTO(
                        host, port, SshHostKeys.type(key), SshHostKeys.fingerprint(key), key);
            }
        } catch (JSchException exception) {
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.SSH_HOST_KEY_PROBE_FAILED);
        } finally {
            if (session != null) session.disconnect();
        }
        throw new BusinessException(
                ResultCode.SERVICE_UNAVAILABLE, CoreMessage.SSH_HOST_KEY_PROBE_FAILED);
    }

    private static final class Capture implements HostKeyRepository {
        private byte[] observed;

        public int check(String host, byte[] key) {
            observed = key.clone();
            return NOT_INCLUDED;
        }

        public void add(HostKey key, UserInfo info) {
            throw new UnsupportedOperationException();
        }

        public void remove(String host, String type) {
            throw new UnsupportedOperationException();
        }

        public void remove(String host, String type, byte[] key) {
            throw new UnsupportedOperationException();
        }

        public String getKnownHostsRepositoryID() {
            return "probe-only";
        }

        public HostKey[] getHostKey() {
            return new HostKey[0];
        }

        public HostKey[] getHostKey(String host, String type) {
            return new HostKey[0];
        }
    }
}
