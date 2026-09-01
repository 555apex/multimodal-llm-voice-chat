# Road Agent DGX 软件使用方法

## 1. 当前运行架构

Road Agent 的 Agent 大模型使用 DGX 本地部署的 Qwen，不调用外部大模型 API；ASR 和 TTS 也在 DGX 本地推理。交通事实以最新版共享 MySQL 为唯一来源，不再调用高德交通接口。

```text
Windows 浏览器
  │ SSH 隧道（Tailscale Serve 获批后可改用 HTTPS）
  ▼
DGX Frontend / Nginx
  └── Backend
        ├── Qwen3.6-35B-A3B-NVFP4（DGX 本地）
        ├── faster-whisper small（DGX CPU）
        ├── Qwen3-TTS 0.6B Serena（DGX GPU）
        └── 外部共享 MySQL（交通数据和业务数据）
```

固定的大模型配置如下：

```dotenv
ROADAGENT_MODEL_ENDPOINT=http://qwen:8000/v1/chat/completions
ROADAGENT_MODEL_NAME=qwen3.6-35b-a3b-nvfp4
ROADAGENT_MODEL_AUTH_ENABLED=false
ROADAGENT_MODEL_ENABLE_THINKING=false
```

`qwen` 是私有 Docker 网络中的 `model-serving-qwen` 容器别名。正常启动不会切换或重启已经健康的 Qwen，也不会影响 Open WebUI。

## 2. Windows 一键启动并查看

打开 PowerShell：

```powershell
cd C:\Users\Lenovo\Desktop\DGX_S534\multimodal-llm-voice-chat-dgx
.\deploy\dgx\start-and-view.ps1
```

脚本会检查并启动 Road Agent、复用本地 Qwen、执行 Qwen/语音/MySQL 交通/三级工作流/Agent SSE 冒烟测试，然后建立 SSH 隧道并打开浏览器。

页面地址：

- 主指挥大屏：<http://127.0.0.1:18080/>
- 独立数字人演示：<http://127.0.0.1:18080/digital-human-demo.html>
- 语音能力：<http://127.0.0.1:18080/api/v1/speech/capabilities>

服务已运行时只建立隧道：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

启动但跳过冒烟：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipSmoke
```

本机 `18080` 被占用时：

```powershell
.\deploy\dgx\start-and-view.ps1 -LocalPort 18081
```

## 3. 页面测试建议

1. 打开主指挥大屏，确认福建静态地图、事件卡片、三级待办和数字人面板正常显示。
2. 查询“福建省普通国省道目前整体交通态势如何？”，确认结果显示 `MYSQL` 事实源。
3. 分别测试“全省国省道通行能力如何？”、“福建区域交通压力如何？”和“福州车型出行特征如何？”。
4. 检查一级上报、二级复核、三级决策页面；生产测试不要随意提交会改变真实事件状态的操作。
5. 允许浏览器使用麦克风，验证中文识别和 Serena 中文语音播放。
6. 打开独立数字人演示页，检查待机、思考和讲解状态。

页面仍显示旧内容时按 `Ctrl+F5` 强制刷新。

## 4. 手工启动流程

登录 DGX：

```powershell
ssh -i C:\Users\Lenovo\.ssh\id_ed25519_dgx_spark whtc@100.119.145.78
```

日常启动：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-workflow --with-agent
```

首次部署或代码更新后先构建：

```bash
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack build
deploy/dgx/dgx-stack up
```

`up` 的行为：

- 创建或复用私有 `dgx-ai` 网络；
- Qwen 未运行时启动 `daily` profile，已运行时保持原模型进程；
- 等待 Qwen 健康后启动 Backend、Speech 和 Frontend；
- 只将前端绑定到 DGX 的 `127.0.0.1:18080`。

## 5. 最新版首次同步与数据库迁移

从 Windows 同步代码，默认保留 DGX 已有 `.env`：

```powershell
.\deploy\dgx\deploy.ps1
```

只有明确要刷新数据库凭据时使用 `-RefreshSecrets`。该选项不读取或覆盖本地 Qwen 配置。

最新版第一次上线前，在 DGX 执行数据库预检：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack db-preflight
```

先完成镜像构建，再进入数分钟维护窗口：

```bash
deploy/dgx/dgx-stack build
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack db-migrate
deploy/dgx/dgx-stack db-verify
deploy/dgx/dgx-stack up
```

迁移前会自动备份受影响表到 `/home/whtc/workspace/backups/road-agent-db/<UTC时间>` 并生成 SHA-256。脚本固定执行五个迁移脚本和一个只读核验脚本，明确不会执行 `20260819_reset_demo_events.sql`。

迁移或验收失败时必须同时回滚数据库和应用。保持 Road Agent 停止，使用实际备份目录名：

```bash
deploy/dgx/dgx-stack db-restore 20260901T120000Z --confirm=20260901T120000Z
```

## 6. 标准测试命令

基础测试：

```bash
deploy/dgx/dgx-stack smoke
```

完整业务冒烟：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-workflow --with-agent
```

稳定性与结构化输出验收：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 100 --with-tts --with-traffic --with-workflow --with-agent
deploy/dgx/dgx-stack smoke --business-structured-iterations 20
deploy/dgx/dgx-stack smoke --tts-concurrency 2 --test-speech-limits
```

这些命令会验证 Qwen 模型名、普通响应、SSE、严格 JSON、交通意图/摘要、资源需求/调度方案、Serena MP3、四类 MySQL 交通查询、三级待办和 Agent SSE。

## 7. 状态与日志

```bash
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack logs
deploy/dgx/dgx-stack logs backend
deploy/dgx/dgx-stack logs frontend
deploy/dgx/dgx-stack logs speech-service
```

查看共享 Qwen 日志：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack logs qwen
```

## 8. 关闭服务

只关闭 Windows 浏览器隧道，不停止 DGX 服务：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Stop
```

停止 Road Agent 应用：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack status
```

`down` 只停止 Backend、Frontend 和 Speech，不停止 Qwen/Open WebUI，不删除模型，不修改 MySQL。

仅在已经启用 Tailscale Serve 时关闭 tailnet HTTPS：

```bash
deploy/dgx/dgx-stack serve-off
```

只有确认没有其他应用和用户使用共享模型时，才可以停止 Qwen：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack stop
```

日常关闭 Road Agent 不要执行 `model-stack stop-all`，它会影响 Open WebUI 和其他模型用户。

## 9. 常见问题

### 页面提示 `MODEL_UPSTREAM_ERROR`

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-agent
```

如果 Qwen 显示 `health: starting`，等待其变为 `healthy`；不要重复启动模型。

### 页面无法打开

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Status
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

### 交通查询失败

先确认数据库迁移已完成，并执行：

```bash
deploy/dgx/dgx-stack db-verify
deploy/dgx/dgx-stack logs backend
```

### 语音不可用但文字功能正常

```bash
deploy/dgx/dgx-stack logs speech-service
deploy/dgx/dgx-stack up
```

Speech 故障不会阻断文字、MySQL 交通查询和三级工作流的基本使用。
