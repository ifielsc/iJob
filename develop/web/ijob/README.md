# iJob Web 1.0.0

Vue / TypeScript 前端。源码与后端 1.0.0 配套，首次安装使用冻结 V1 数据库基线。项目入口见[根 README](../../../README.md)。

## 开发与构建

Node 24.14.1、pnpm 11.19.0，在本目录执行：

```sh
pnpm install --frozen-lockfile
pnpm check
pnpm dev
```

开发服务器仅监听 127.0.0.1:5173。默认不代理后端；复制[模板](../../../document/examples/deployment/frontend.env.local.example)为本目录 `.env.local`，填写 `IJOB_DEV_API_TARGET` 指向明确的隔离开发后端。修改后重启 Vite。这里不配置数据库密码、SMTP、AES 或登录口令，也不把秘密放入任何 `VITE_*` 变量。

`pnpm build` 生成 dist；生产发布静态文件，不运行 Vite。API 使用同源 `/api`，反向代理保留原路径、Token 响应头和 Idempotency-Key，history 路由回退 index.html。部署模板见 [nginx](../../../document/examples/deployment/nginx.conf.example)，首次安装、认证及多实例见[启动说明](../../deployment/first-run/README.md)。

## 测试

`pnpm check` 包含版本、类型、lint、边界、格式、单元测试和构建。安装 Playwright Chromium 后执行 `pnpm test:e2e`。完整脚本位于 [verify-frontend.ps1](../../../test/tools/verify-frontend.ps1)，只使用新证据 RunId；输出被根忽略规则排除。

集成测试使用独立临时 SQLite 数据库，需先构建后端 1.0.0，设置 `IJOB_TEST_PASSWORD` 和 `IJOB_TEST_JAVA_HOME`（或 `JAVA_HOME`，JDK 21）。辅助脚本默认查找后端和 SPI 的 1.0.0 JAR；自定义构件分别通过 `IJOB_TEST_BACKEND_JAR`、`IJOB_TEST_PLUGIN_SPI_JAR` 指定。必要时用 `IJOB_TEST_CRYPTO_JAR` 指定 spring-security-crypto JAR。

仅需要 SQL fixture 的测试显式开启 `IJOB_TEST_SQL_FIXTURE=true`，并设置 `IJOB_TEST_PG_BIN` 为 PostgreSQL 二进制目录。`IJOB_TEST_*` 只用于隔离测试，不能指向日常运行库或 QA/cluster。不同 Playwright 配置的测试集合分开执行，不将测试发现列表当作实际通过记录。

保留 pnpm 锁文件、workspace 配置和 patches；更新依赖需重新验证受控补丁和构建。Token 只在内存保存，刷新需要重新登录。业务调度时区与普通 API UTC 时间分别处理。
