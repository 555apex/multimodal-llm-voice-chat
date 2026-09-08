# Road Agent DGX 简明启动指南

Road Agent 的 Agent 大模型、ASR 和 TTS 均在 DGX 本地运行：

- Agent：`qwen3.6-35b-a3b-nvfp4`
- ASR：faster-whisper small
- TTS：Qwen3-TTS 0.6B Serena

交通与业务数据仍使用共享 MySQL，因此完整功能需要 DGX 能访问该数据库。

## 一、在 DGX 主机上启动并使用

### 1. 日常启动

打开 DGX 终端：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
```

确认以下服务显示 `healthy`：

```text
road-agent-dgx-frontend
road-agent-dgx-backend
road-agent-dgx-speech
model-serving-qwen
```

### 2. 功能检查

```bash
deploy/dgx/dgx-stack smoke \
  --model-iterations 3 \
  --with-tts \
  --with-traffic \
  --with-workflow \
  --with-agent
```

### 3. 打开页面

在 DGX 本机浏览器访问：

- 主指挥大屏：<http://127.0.0.1:18080/>
- 数字人演示：<http://127.0.0.1:18080/digital-human-demo.html>
- 语音能力：<http://127.0.0.1:18080/api/v1/speech/capabilities>

### 4. 首次部署或代码更新后

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack build
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
```

日常启动不需要重复执行模型下载、数据库迁移或镜像构建。

### 5. 查看日志

```bash
deploy/dgx/dgx-stack logs
deploy/dgx/dgx-stack logs backend
deploy/dgx/dgx-stack logs frontend
deploy/dgx/dgx-stack logs speech-service
```

查看 Qwen 日志：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack logs qwen
```

### 6. 停止 Road Agent

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack status
```

`down` 只停止 Road Agent 的前端、后端和语音服务，不停止 Qwen/Open WebUI，不删除模型，也不修改 MySQL。

## 二、从 Windows 本机远程启动并使用

### 1. 一键启动

先确认 Windows 已连接 Tailscale，然后打开 PowerShell：

```powershell
cd C:\Users\Lenovo\Desktop\DGX_S534\multimodal-llm-voice-chat-dgx
.\deploy\dgx\start-and-view.ps1
```

脚本会启动或检查 DGX Road Agent、复用本地 Qwen、执行冒烟测试、建立 SSH 隧道并打开浏览器。

Windows 浏览器访问：

- 主页面：<http://127.0.0.1:18080/>
- 数字人演示：<http://127.0.0.1:18080/digital-human-demo.html>

### 2. 常用参数

跳过完整冒烟测试：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipSmoke
```

DGX 服务已运行，只建立访问隧道：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

查看隧道状态：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Status
```

本机 `18080` 端口被占用时：

```powershell
.\deploy\dgx\start-and-view.ps1 -LocalPort 18081
```

随后访问 <http://127.0.0.1:18081/>。

### 3. 关闭 Windows 访问隧道

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Stop
```

该命令只关闭 Windows SSH 隧道，DGX Road Agent 仍继续运行。

### 4. 从 Windows 远程停止 Road Agent

```powershell
ssh -i C:\Users\Lenovo\.ssh\id_ed25519_dgx_spark `
  whtc@100.119.145.78 `
  "cd /home/whtc/workspace/projects/road-agent-dgx && deploy/dgx/dgx-stack down"
```

## 三、常见问题

页面提示 `MODEL_UPSTREAM_ERROR`：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-agent
```

Windows 页面无法打开：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Status
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

语音不可用：

```bash
deploy/dgx/dgx-stack logs speech-service
deploy/dgx/dgx-stack up
```

> 日常不要执行 `scripts/model-stack stop-all`，否则会影响 Qwen、Open WebUI 和其他模型用户。
