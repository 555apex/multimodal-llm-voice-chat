# Road Agent DGX Spark 部署

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
