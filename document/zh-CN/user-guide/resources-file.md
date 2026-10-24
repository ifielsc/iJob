# LOCAL、FTP、SFTP 与 S3 文件资源

[English](../../en/user-guide/resources-file.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → 文件资源。页面动作随当前账号权限提供；先确认操作对象及环境。

## 配置和动作说明

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `protocol / basePath` | LOCAL 根路径属于执行机器；远端路径或对象前缀属于远端资源，不是浏览器上传目录。 |
| `host / port / username / password / timeout` | FTP/SFTP 连接身份和超时；仅授权测试目录。 |
| `privateKey / passphrase / hostKey / knownHosts` | SFTP 私钥及主机信任，与 SSH 资源相同原则。 |
| `endpoint / region / bucket / accessKey / secretKey / sessionToken / pathStyleAccess` | S3 服务、区域、桶、凭据与寻址风格，按服务实际兼容性配置。 |

## 操作与结果核对

先在资源根准备 orders.csv，再测试列目录/读取，最后对独立目标做写入验证。浏览器允许根列表不限制脚本的所有磁盘访问。不要把平台 runtime 或 secrets 当作传输源。
