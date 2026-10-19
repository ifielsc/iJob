# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Disposable authenticated loopback SSH server for browser integration tests only."""
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import threading
import paramiko

directory = Path(sys.argv[1]).resolve()
shell = os.environ["IJOB_TEST_SH"]
os.environ["PATH"] = str(Path(shell).parent) + os.pathsep + os.environ["PATH"]
password = os.environ["IJOB_TEST_PASSWORD"]
key = paramiko.ECDSAKey.generate(bits=256)
listener = socket.socket()
listener.bind(("127.0.0.1", 0))
listener.listen(8)
port = listener.getsockname()[1]
known = directory / "ssh-known-hosts"
known.write_text(f"[127.0.0.1]:{port} {key.get_name()} {key.get_base64()}\n", encoding="utf-8")


class Server(paramiko.ServerInterface):
    def __init__(self):
        self.ready = threading.Event()
        self.command = ""

    def check_auth_password(self, username, provided):
        with (directory / "ssh-auth-attempts").open("a") as log:
            log.write("password\n")
        return paramiko.AUTH_SUCCESSFUL if username == "fixture" and provided == password else paramiko.AUTH_FAILED

    def check_auth_none(self, username):
        with (directory / "ssh-auth-attempts").open("a") as log:
            log.write("none\n")
        return paramiko.AUTH_FAILED

    def get_allowed_auths(self, username):
        return "password"

    def check_channel_request(self, kind, channel_id):
        return paramiko.OPEN_SUCCEEDED if kind == "session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def check_channel_exec_request(self, channel, command):
        self.command = command.decode("utf-8")
        self.ready.set()
        return True


def connection(client):
    transport = paramiko.Transport(client)
    try:
        transport.add_server_key(key)
        server = Server()
        transport.start_server(server=server)
        channel = transport.accept(10)
        if channel is None or not server.ready.wait(10):
            return
        channel.settimeout(10)
        script = bytearray()
        if server.command != "true":
            while True:
                part = channel.recv(32768)
                if not part:
                    break
                script.extend(part)
                if len(script) > 8 * 1024 * 1024:
                    raise ValueError("Fixture script too large")
        result = subprocess.run([shell, "-c", server.command], input=bytes(script), capture_output=True,
                                timeout=10, cwd=directory, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if result.stdout:
            channel.sendall(result.stdout)
        if result.stderr:
            channel.sendall_stderr(result.stderr)
        channel.send_exit_status(result.returncode)
        channel.shutdown_write()
        channel.close()
    except (EOFError, OSError, paramiko.SSHException):
        pass  # Expected during wrong-host-key and aborted connection tests.
    finally:
        transport.close()


print(json.dumps({"port": port, "knownHosts": str(known)}), flush=True)
while True:
    client, _ = listener.accept()
    rotate = directory / "ssh-rotate"
    if rotate.exists():
        key = paramiko.ECDSAKey.generate(bits=256)
        rotate.unlink()
    threading.Thread(target=connection, args=(client,), daemon=True).start()
