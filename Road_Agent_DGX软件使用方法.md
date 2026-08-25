# Road Agent DGX 软件使用方法

## 1. 运行方式说明

Road Agent 当前运行在 DGX Spark 上，Agent 大模型使用 DGX 本地部署的 Qwen，不调用外部大模型 API。

```text
Windows 浏览器
  │ SSH 隧道或 Tailscale HTTPS
  ▼
DGX Frontend/Nginx
  └── Backend
        ├── model-serving-qwen：Qwen3.6-35B
        ├── Speech：faster-whisper ASR + Qwen3-TTS Serena
        ├── 外部共享 MySQL
        └── 高德 API
```

Backend 当前模型配置：

```dotenv
ROADAGENT_MODEL_ENDPOINT=http://qwen:8000/v1/chat/completions
ROADAGENT_MODEL_NAME=qwen3.6-35b-a3b-nvfp4
ROADAGENT_MODEL_AUTH_ENABLED=false
ROADAGENT_MODEL_ENABLE_THINKING=false
```

`qwen` 是 DGX Docker 内部网络中的 `model-serving-qwen` 容器别名，不是外部 API 地址。

## 2. 推荐：从 Windows 一键启动并查看

打开 PowerShell，执行：

```powershell
cd C:\Users\Lenovo\Desktop\DGX_S534\multimodal-llm-voice-chat-dgx
.\deploy\dgx\start-and-view.ps1
```

脚本会依次完成：

1. 通过 SSH 连接 DGX。
2. 检查并启动本地 Qwen、Backend、Frontend 和 Speech。
3. 显示容器及资源状态。
4. 运行 Qwen、TTS、高德和 Agent SSE 冒烟测试。
5. 建立本机 `127.0.0.1:18080` 到 DGX 的 SSH 隧道。
6. 在默认浏览器中打开主页面。

该启动流程是幂等的：Qwen 已健康运行时不会重启模型进程。

如服务已经运行，只希望重新建立浏览器隧道：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

如希望启动服务但跳过完整冒烟测试：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipSmoke
```

## 3. 页面地址与测试方法

SSH 隧道建立后使用以下地址：

- 主页面：<http://127.0.0.1:18080/>
- 数字人演示：<http://127.0.0.1:18080/digital-human-demo.html>
- 语音能力接口：<http://127.0.0.1:18080/api/v1/speech/capabilities>

建议依次测试：

1. 在主页面输入“福州五四路现在堵吗？”。
2. 确认页面返回基于高德实时数据的路况，而不是模型编造数据。
3. 允许浏览器使用麦克风，录制“福州五四路现在拥堵吗”。
4. 确认识别文本正确，并能播放 Serena 中文语音。
5. 打开数字人演示页，检查待机、思考和讲解状态。

若浏览器仍显示旧的错误提示，可按 `Ctrl+F5` 强制刷新后重新发送消息。

## 4. Windows 隧道管理

查看隧道及页面状态：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Status
```

关闭本机 SSH 隧道：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Stop
```

关闭隧道只会终止 Windows 到 DGX 的本机转发，不会停止 DGX 上的 Road Agent 或 Qwen。

如果本机 `18080` 端口被其他程序占用，可以换一个本地端口：

```powershell
.\deploy\dgx\start-and-view.ps1 -LocalPort 18081
```

对应访问地址为 <http://127.0.0.1:18081/>。

## 5. 在 DGX 上手工启动

登录 DGX：

```powershell
ssh -i C:\Users\Lenovo\.ssh\id_ed25519_dgx_spark whtc@100.119.145.78
```

在 DGX 中执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
```

`up` 会执行以下操作：

- 确保 `dgx-ai` Docker 网络存在。
- Qwen 未运行时启动 `daily` profile；已运行时保持原模型进程。
- 等待本地 Qwen 健康。
- 启动 Backend、Speech 和 Frontend。
- 等待页面和 Speech 健康检查通过。

首次部署或镜像需要重建时使用：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack build
deploy/dgx/dgx-stack up
```

模型已经下载并通过 manifest 校验，日常启动不需要重复执行 `download-models`。

## 6. 标准测试命令

基础冒烟：

```bash
deploy/dgx/dgx-stack smoke
```

推荐的完整业务冒烟：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-agent
```

完整测试会验证：

- Qwen 模型名、普通响应、流式响应和严格 JSON。
- ASR/TTS 能力接口和 Serena MP3。
- 高德实时路况查询。
- Agent 意图识别、Skill、Tool 和 SSE 完整事件流。

生产稳定性测试：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 100 --with-tts --with-traffic --with-agent
deploy/dgx/dgx-stack smoke --business-structured-iterations 20
deploy/dgx/dgx-stack smoke --tts-concurrency 2 --test-speech-limits
```

## 7. 状态和日志

查看运行状态：

```bash
deploy/dgx/dgx-stack status
```

查看全部 Road Agent 日志：

```bash
deploy/dgx/dgx-stack logs
```

查看单个服务日志：

```bash
deploy/dgx/dgx-stack logs backend
deploy/dgx/dgx-stack logs frontend
deploy/dgx/dgx-stack logs speech-service
```

查看 Qwen 日志：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack logs qwen
```

## 8. 关闭服务

### 8.1 只关闭本机浏览器隧道

在 Windows 项目目录执行：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Stop
```

DGX 服务继续运行，下次执行一键启动脚本即可重新查看。

### 8.2 停止 Road Agent 应用

在 DGX 执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack down
```

该命令停止并移除 Road Agent 的 Backend、Frontend 和 Speech 容器，但：

- 不停止共享 Qwen。
- 不停止 Open WebUI。
- 不删除模型文件。
- 不修改共享 MySQL。

停止后检查：

```bash
deploy/dgx/dgx-stack status
```

### 8.3 关闭 Tailscale Serve

仅在已经配置 Tailscale Serve 时执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack serve-off
```

该命令关闭 tailnet HTTPS 路由，不停止应用容器。

### 8.4 可选：停止共享 Qwen 释放模型资源

只有在确认 Open WebUI 和其他应用当前不再使用 Qwen 时才执行：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack stop
```

该命令停止 Qwen/GPT 等重模型容器、释放模型资源，但保留 Open WebUI 容器。停止后 Road Agent 的模型请求将不可用。

如需连 Open WebUI 一起停止：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack stop-all
```

`stop-all` 会影响其他使用 Open WebUI 或 model-serving 的用户，日常关闭 Road Agent 时不要执行。

### 8.5 推荐的完整关闭顺序

1. Windows 关闭 SSH 隧道：

   ```powershell
   .\deploy\dgx\start-and-view.ps1 -Action Stop
   ```

2. DGX 停止 Road Agent：

   ```bash
   cd /home/whtc/workspace/projects/road-agent-dgx
   deploy/dgx/dgx-stack serve-off
   deploy/dgx/dgx-stack down
   ```

3. 仅在无人使用共享模型时停止 Qwen：

   ```bash
   cd /home/whtc/workspace/projects/model-serving
   scripts/model-stack stop
   ```

## 9. 重新启动

停止 Road Agent 后重新启动：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-agent
```

如果共享 Qwen 也已停止，`dgx-stack up` 会自动重新启动本地 Qwen，并等待模型健康后再启动应用。模型重新加载需要一定时间，请等待脚本完成，不要在模型处于 `health: starting` 时测试页面。

随后在 Windows 执行：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

## 10. 常见问题

### 页面提示 `MODEL_UPSTREAM_ERROR`

先在 DGX 检查状态：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack status
```

如果 Qwen 为 `health: starting`，等待其变为 `healthy`。如果 Qwen 未运行，执行：

```bash
deploy/dgx/dgx-stack up
```

然后运行完整冒烟并刷新浏览器：

```bash
deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-agent
```

### 页面无法打开

在 Windows 检查隧道：

```powershell
.\deploy\dgx\start-and-view.ps1 -Action Status
```

如未运行，重新建立：

```powershell
.\deploy\dgx\start-and-view.ps1 -SkipRemoteStart
```

### 语音不可用但文字功能正常

查看 Speech 日志并重新启动应用：

```bash
deploy/dgx/dgx-stack logs speech-service
deploy/dgx/dgx-stack up
```

Speech 故障不会阻断文字、高德和 Agent 的基本业务流程。
