# Road Agent DGX Spark 部署

> 2026-09-08 合并版本：本次升级和回滚以 `docs/DGX_MERGE_20260908.md` 为准。
> 下方保留的首次部署、初始化和公网账号创建步骤不应用于现有生产库；本次只做已核验的增量授权与演示流程重置。
> 内网分类轮询关闭，公网分类轮询开启；两个后端共享业务数据库。模型声明见 `configs/models.yaml`。

目标主机为 `spark-8a8d`（ARM64 / NVIDIA GB10），项目目录为
`/home/whtc/workspace/projects/road-agent-dgx`。

## 1. 从 Windows 同步最新版代码

在迁移副本根目录执行：

```powershell
.\deploy\dgx\deploy.ps1
```

脚本先在 DGX 的 `/home/whtc/workspace/backups/road-agent-project` 归档旧项目，再完整替换代码；默认保留远端现有的 `.env` 和 `.env.db-admin`，不会从本机 DeepSeek 或其他配置覆盖本地 Qwen，也不会上传密钥。

只有明确需要刷新数据库凭据时才执行：

```powershell
.\deploy\dgx\deploy.ps1 -RefreshSecrets -SecretsFile C:\安全目录\api-test.ps1
```

刷新时只读取 `ROADAGENT_DB_URL`、`ROADAGENT_DB_USERNAME` 和 `ROADAGENT_DB_PASSWORD`。运行环境文件始终设为 `600`，不进入 Git 或项目归档。

## 2. 预检、模型与镜像构建

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack download-models  # 仅模型尚未登记时执行
deploy/dgx/dgx-stack build
```

固定语音模型：

- `Systran/faster-whisper-small@536b0662742c02347bc0e980a01041f333bce120`
- `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice@f3d1af06e4eaefac12b1ffa6726f9eef674a6f02`

Agent 使用现有 `model-serving-qwen` 中的 `qwen3.6-35b-a3b-nvfp4`。`up` 发现 Qwen 已运行时只复用并检查健康状态，不切换或重启模型。

## 3. 首次升级的数据库维护窗口

新版交通事实来自共享 MySQL，并新增三级应急工作流与资源库存结构。迁移客户端固定为支持 ARM64 的 MySQL 官方镜像 `mysql:8.4.11-oraclelinux9`；DGX 通过 `dockerproxy.net` 拉取同一镜像内容。先预检：

```bash
deploy/dgx/dgx-stack db-preflight
```

若应用账号没有 DDL/备份权限，在 `deploy/dgx/.env.db-admin` 中只配置三项数据库变量并执行 `chmod 600 deploy/dgx/.env.db-admin`。该文件不会进入 Git 或同步归档。

构建完成后进入维护窗口，仅停止 Road Agent：

```bash
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack db-migrate
deploy/dgx/dgx-stack db-verify
```

`db-migrate` 会先在 `/home/whtc/workspace/backups/road-agent-db/<UTC时间>` 生成一致性结构与数据备份、行数/字段/外键清单及 SHA-256，然后严格按以下顺序执行：

1. `20260728_emergency_dispatch.sql`
2. `20260819_three_level_emergency_workflow.sql`
3. `20260820_emergency_resource_dispatch.sql`
4. `20260820_seed_demo_emergency_resources.sql`
5. `20260820_level3_provincial_decision_metadata.sql`
6. `20260820_verify_emergency_resources.sql`（只读核验）

运维脚本不会执行 `20260819_reset_demo_events.sql`。数据库迁移和恢复都要求 Road Agent 三个容器已停止，Qwen 与 Open WebUI 不受影响。

如迁移或新版验收失败，先保持 Road Agent 停止，然后使用备份目录名显式确认恢复：

```bash
deploy/dgx/dgx-stack db-restore 20260901T120000Z --confirm=20260901T120000Z
```

新版包含非空字段，不能只回滚应用而不恢复数据库。

## 4. 启动与验证

```bash
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-workflow --with-agent
```

稳定性验收：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 100 --with-tts --with-traffic --with-workflow --with-agent
deploy/dgx/dgx-stack smoke --business-structured-iterations 20
deploy/dgx/dgx-stack smoke --tts-concurrency 2 --test-speech-limits
```

冒烟测试覆盖本地 Qwen 普通/流式/结构化响应、`enable_thinking=false` 业务输出、Serena TTS、四类 MySQL 交通查询、三级工作流只读接口和 Agent SSE。

## 5. Windows 一键查看

```powershell
.\deploy\dgx\start-and-view.ps1
```

该脚本启动远端应用、执行完整冒烟、建立本机 SSH 隧道并打开：

- 主指挥大屏：`http://127.0.0.1:18080/`
- 独立数字人演示：`http://127.0.0.1:18080/digital-human-demo.html`

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Status
.\deploy\dgx\start-and-view.ps1 -Action Stop
```

`-Action Stop` 只关闭 Windows SSH 隧道，不停止 DGX 服务。

## 6. 日常运维

```bash
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack logs
deploy/dgx/dgx-stack logs backend
deploy/dgx/dgx-stack logs speech-service
deploy/dgx/dgx-stack down
```

`down` 只停止 Road Agent Backend、Frontend 和 Speech，不停止 Qwen/Open WebUI，不删除模型，也不修改 MySQL。

Tailscale Serve 获管理员授权后可执行 `dgx-stack serve`，关闭使用 `dgx-stack serve-off`；授权前继续使用 SSH 隧道。

## 7. 公网完整功能测试环境

公网 Backend 直接使用原数据库地址，但改用自动生成的受限账号 `roadagent_public_app`：7 张交通表仅有 `SELECT`，8 张应急业务表仅有 `SELECT/INSERT/UPDATE/DELETE`。受限凭据写入项目外的 `/home/whtc/.config/road-agent/public-original-db.env`；数据库端口不向宿主机或公网发布。

启用前已备份全部 8 张可写应急业务表，备份及 SHA-256 校验清单位于：

```text
/home/whtc/workspace/backups/road-agent-db/public-shared-db-before-enable-20260904
```

先在 DGX 的交互式终端设置固定登录账号和密码：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack public-auth-set
```

密码不会明文保存，只有密码哈希写入：

```text
/home/whtc/.config/road-agent/public.htpasswd
```

随后启用公网环境：

```bash
deploy/dgx/dgx-stack public-on
deploy/dgx/dgx-stack public-status
deploy/dgx/dgx-stack public-db-access-verify
```

公网 HTTPS 地址固定为：

```text
https://spark-8a8d.taile1b178.ts.net/
```

停用公网入口但保留账号哈希和原数据库备份：

```bash
deploy/dgx/dgx-stack public-off
```

完整操作与安全说明见项目根目录 `Road_Agent_DGX公网测试使用文档.md`。

## 8. 切换到新的共享数据库

原数据库不需要删除。准备一个权限为 `600`、只包含以下三项的新库凭据文件，并放在 DGX 的项目目录之外：

```text
ROADAGENT_DB_URL=jdbc:mysql://host:port/schema?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
ROADAGENT_DB_USERNAME=...
ROADAGENT_DB_PASSWORD=...
```

先暂存并只读核验。暂存不会修改运行中的容器或当前数据库连接：

```bash
deploy/dgx/dgx-stack db-switch-stage /secure/new-roadagent-db.env
deploy/dgx/dgx-stack db-switch-verify
deploy/dgx/dgx-stack db-switch-prepare
```

`db-switch-prepare` 会对新版应用涉及的表执行一致性逻辑备份，并将备份恢复到一次性隔离 MySQL 容器中；只有表级行数核对一致后才允许正式切换。

公网数据库账号继续使用 `roadagent_public_app`。如果该账号尚未在新库创建，由数据库负责人提供临时管理员凭据文件后执行授权，再重新只读核验：

```bash
deploy/dgx/dgx-stack db-switch-provision-public /secure/new-roadagent-db-admin.env
deploy/dgx/dgx-stack db-switch-verify
```

核验通过后，在维护窗口原子切换内外网后端。工具只替换数据库三项配置，保留本地模型和语音参数，并在 `/home/whtc/workspace/backups/road-agent-db-switch/` 保存旧配置：

```bash
deploy/dgx/dgx-stack db-switch-activate
```

需要恢复旧连接时执行：

```bash
deploy/dgx/dgx-stack db-switch-rollback
```

切换前后均不得运行演示数据重置脚本。旧库停止应用写入后保留七天，由负责人另行决定归档或删除。
