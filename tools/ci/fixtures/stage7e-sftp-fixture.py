# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Loopback-only SFTP fixture; no business files or credentials."""
import json, socket, sys, threading, stat, logging, warnings
warnings.filterwarnings("ignore")
import paramiko
logging.getLogger("paramiko").setLevel(logging.CRITICAL)
legacy='--legacy' in sys.argv
key=paramiko.RSAKey.generate(bits=2048) if legacy else paramiko.ECDSAKey.generate(bits=256)
listener=socket.socket();listener.bind(("127.0.0.1",0));listener.listen()
auth_count=0
class Server(paramiko.ServerInterface):
    def check_auth_password(self, username, password):
        global auth_count
        auth_count+=1
        return paramiko.AUTH_SUCCESSFUL if username=="fixture" and password=="fixture-password" else paramiko.AUTH_FAILED
    def get_allowed_auths(self, username): return "password"
    def check_channel_request(self, kind, chanid): return paramiko.OPEN_SUCCEEDED if kind=="session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
class Files(paramiko.SFTPServerInterface):
    def stat(self,path):
        if path!="/": return paramiko.SFTP_NO_SUCH_FILE
        item=paramiko.SFTPAttributes();item.st_mode=stat.S_IFDIR|0o700;item.st_size=0;item.st_mtime=1
        return item
    lstat=stat
    def list_folder(self,path):
        if path!="/": return paramiko.SFTP_NO_SUCH_FILE
        item=paramiko.SFTPAttributes();item.filename="report.csv";item.st_mode=stat.S_IFREG|0o600;item.st_size=7;item.st_mtime=1
        return [item]
transports=[]
def handle(connection):
    transport=paramiko.Transport(connection);transports.append(transport);transport.add_server_key(key)
    if legacy:
        options=transport.get_security_options()
        options.kex=('diffie-hellman-group14-sha1',)
        options.key_types=('ssh-rsa',)
    transport.set_subsystem_handler("sftp",paramiko.SFTPServer,Files)
    try: transport.start_server(server=Server())
    except Exception: pass
def accept():
    while True:
        try: connection,_=listener.accept()
        except OSError:return
        threading.Thread(target=handle,args=(connection,),daemon=True).start()
threading.Thread(target=accept,daemon=True).start()
print(json.dumps({"port":listener.getsockname()[1],"key":key.get_base64()}),flush=True)
for line in sys.stdin:
    if line.strip()=="count": print(auth_count,flush=True)
    else:break
listener.close()
for transport in transports:transport.close()
