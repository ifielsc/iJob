# 内部辅助文件 / Internal support files

此目录包含入口调用的 PowerShell/Bash 辅助脚本和原生日志配置，不作为用户直接运行的入口。请保留完整目录，并通过[程序包入口](../README.md)操作。

This directory contains PowerShell/Bash helpers and native logging configuration used by the entry points. Keep it intact and use the [package entry points](../README.md) instead of invoking these files directly.

Windows 实例控制目录只复制 support/instance.ps1；进程管理与日志配置仍从 instance.conf 指定的完整程序包读取。

A Windows instance control directory copies only support/instance.ps1. Process management and logging configuration remain in the complete program bundle selected by instance.conf.
