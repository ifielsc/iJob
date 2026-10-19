# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Loopback, memory-only FTP/SFTP publication fixture. No user files are accessed."""
import io, json, logging, posixpath, socket, socketserver, stat, sys, threading, warnings
warnings.filterwarnings('ignore')
logging.getLogger('paramiko').setLevel(logging.CRITICAL)
files={'/out/data.csv':b'old','/out/reject.csv':b'old'}
dirs={'/','/out'}
def path(value): return posixpath.normpath('/'+value.lstrip('/'))
def rename(source,target):
    if target.endswith('/reject.csv'): return False
    if source not in files: return False
    files[target]=files.pop(source); return True
if sys.argv[1]=='FTP':
    class FTP(socketserver.StreamRequestHandler):
        def reply(self,text): self.wfile.write((text+'\r\n').encode()); self.wfile.flush()
        def handle(self):
            self.cwd='/'; self.data=None; self.source=None; self.reply('220 fixture')
            try:
                for line in self.rfile:
                    command,_,arg=line.decode().strip().partition(' '); command=command.upper(); p=path(arg)
                    if command=='USER': self.reply('331 password')
                    elif command=='PASS': self.reply('230 accepted')
                    elif command=='SYST': self.reply('215 UNIX Type: L8')
                    elif command=='TYPE': self.reply('200 type')
                    elif command=='PWD': self.reply('257 "'+self.cwd+'"')
                    elif command=='CWD':
                        if p in dirs: self.cwd=p; self.reply('250 cwd')
                        else: self.reply('550 missing')
                    elif command=='MKD': dirs.add(p); self.reply('257 created')
                    elif command=='PASV':
                        self.data=socket.socket(); self.data.bind(('127.0.0.1',0)); self.data.listen(1)
                        port=self.data.getsockname()[1]; self.reply(f'227 Entering Passive Mode (127,0,0,1,{port//256},{port%256})')
                    elif command in ['LIST','STOR','RETR']:
                        self.reply('150 data'); conn,_=self.data.accept()
                        with conn:
                            if command=='STOR':
                                content=bytearray()
                                while True:
                                    block=conn.recv(65536)
                                    if not block:break
                                    content.extend(block)
                                files[p]=bytes(content)
                            elif command=='RETR': conn.sendall(files[p])
                            else:
                                names=[k for k in files if k==p or posixpath.dirname(k)==p]
                                conn.sendall(''.join(f'-rw-r--r-- 1 fixture fixture {len(files[k])} Oct 15 2026 {posixpath.basename(k)}\r\n' for k in names).encode())
                        self.data.close(); self.data=None; self.reply('226 complete')
                    elif command=='RNFR': self.source=p; self.reply('350 ready')
                    elif command=='RNTO': self.reply('250 renamed' if rename(self.source,p) else '550 rejected')
                    elif command=='DELE': self.reply('250 deleted' if files.pop(p,None) is not None else '550 missing')
                    elif command=='QUIT': self.reply('221 bye'); break
                    else:self.reply('200 accepted')
            finally:
                if self.data: self.data.close()
    server=socketserver.ThreadingTCPServer(('127.0.0.1',0),FTP); server.daemon_threads=True
    threading.Thread(target=server.serve_forever,daemon=True).start()
    print(json.dumps({'port':server.server_address[1]}),flush=True)
    sys.stdin.readline(); server.shutdown(); server.server_close()
else:
    import paramiko
    key=paramiko.ECDSAKey.generate(bits=256)
    class Auth(paramiko.ServerInterface):
        def check_auth_password(self,u,p): return paramiko.AUTH_SUCCESSFUL if (u,p)==('fixture','fixture-password') else paramiko.AUTH_FAILED
        def get_allowed_auths(self,u): return 'password'
        def check_channel_request(self,k,i): return paramiko.OPEN_SUCCEEDED if k=='session' else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
    class Handle(paramiko.SFTPHandle):
        def __init__(self,p): super().__init__(); self.p=p
        def read(self,offset,length): return files[self.p][offset:offset+length]
        def write(self,offset,data):
            old=files[self.p]; files[self.p]=old[:offset]+data+old[offset+len(data):]; return paramiko.SFTP_OK
    class Files(paramiko.SFTPServerInterface):
        def stat(self,p):
            p=path(p)
            if p not in files and p not in dirs:return paramiko.SFTP_NO_SUCH_FILE
            a=paramiko.SFTPAttributes(); a.st_mode=(stat.S_IFDIR if p in dirs else stat.S_IFREG)|0o700; a.st_size=len(files.get(p,b'')); a.st_mtime=1; a.st_uid=0; a.st_gid=0; return a
        lstat=stat
        def list_folder(self,p):
            result=[]
            for k in files:
                if posixpath.dirname(k)==path(p):
                    a=self.stat(k); a.filename=posixpath.basename(k); result.append(a)
            return result
        def open(self,p,flags,attrs):
            import os
            p=path(p)
            if flags&os.O_CREAT: files.setdefault(p,b'')
            if flags&os.O_TRUNC: files[p]=b''
            return Handle(p) if p in files else paramiko.SFTP_NO_SUCH_FILE
        def mkdir(self,p,attrs): dirs.add(path(p)); return paramiko.SFTP_OK
        def remove(self,p): return paramiko.SFTP_OK if files.pop(path(p),None) is not None else paramiko.SFTP_NO_SUCH_FILE
        def rename(self,s,t): return paramiko.SFTP_OK if rename(path(s),path(t)) else paramiko.SFTP_PERMISSION_DENIED
        posix_rename=rename
    listener=socket.socket(); listener.bind(('127.0.0.1',0)); listener.listen(); transports=[]
    def accept():
        while True:
            try:c,_=listener.accept()
            except OSError:return
            t=paramiko.Transport(c); transports.append(t); t.add_server_key(key); t.set_subsystem_handler('sftp',paramiko.SFTPServer,Files)
            try:t.start_server(server=Auth())
            except Exception:pass
    threading.Thread(target=accept,daemon=True).start()
    print(json.dumps({'port':listener.getsockname()[1],'key':key.get_base64()}),flush=True)
    sys.stdin.readline(); listener.close()
    for t in transports:t.close()
