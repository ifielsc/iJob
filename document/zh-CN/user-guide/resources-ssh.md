# SSH 资源与主机信任

[English](../../en/user-guide/resources-ssh.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → SSH 资源。页面动作随当前账号权限提供；先确认操作对象及环境。

## 配置和动作说明

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `host / port / username` | 目标主机、SSH 端口和独立远程账号。 |
| `password / privateKey / passphrase` | 按认证方式提供口令或私钥及解锁口令，遵循秘密保留规则。 |
| `hostKeyVerification / hostKey / knownHosts` | 从可信渠道核对主机指纹/known_hosts；首次连接不得盲目信任未知主机。 |
| `connectTimeout` | 连接超时与脚本执行超时独立。 |

## 操作与结果核对

测试连接后，用 SCRIPT/SSH 执行 `echo hello-ijob`，确认实际远程用户、目录和解释器。主机密钥改变先核对原因，不通过关闭校验消除报错。SFTP 文件资源也需独立的路径/权限与信任配置。
