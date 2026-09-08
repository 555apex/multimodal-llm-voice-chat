# Open WebUI 双模型接入运行手册

> 2026-09-08 更新：Qwen 支持服务端切换 Qwen3.8 FP8 / Qwen3.6 NVFP4，宿主机 API 改为 `127.0.0.1:8001`。当前操作见 [Qwen3.8 部署与切换指南](../Qwen3.8_DGX部署与模型切换指南.md)。下文 Qwen3.6 固定名称和 Tailscale 8001 地址属于历史部署说明。

本手册接续已经完成的 Qwen3.6 与 GPT-OSS-120B 部署。Open WebUI 常驻，两个重型模型互斥运行。模型下拉框会一直显示两个模型，但只能向当前已启动的模型发送请求。

## 1. 从 Windows 一键同步并安装（推荐）

在 Windows PowerShell 中执行：

```powershell
Set-Location "C:\Users\Lenovo\Desktop\DGX_S534"
.\deploy-open-webui.ps1
```

脚本使用已有的 `dgx-spark` SSH 别名，先把 DGX 当前配置备份到 `artifacts/open-webui-config-backup-时间.tgz`，再上传文件并执行安装。它不会上传或覆盖 DGX 上已有的 `.env`。

关键输出会依次出现：

```text
[1/4] Testing SSH connection to dgx-spark
[2/4] Backing up the current DGX configuration
[3/4] Uploading Open WebUI configuration and scripts
[4/4] Installing Open WebUI on the DGX
```

如果你的 SSH 别名不是 `dgx-spark`：

```powershell
.\deploy-open-webui.ps1 -SshHost "你的SSH别名"
```

以下手动步骤只在不使用一键脚本时需要。

## 2. 手动把本项目同步到 DGX

最终目录必须是：

```text
/home/whtc/workspace/projects/model-serving
```

在 DGX 的终端执行：

```bash
cd /home/whtc/workspace/projects/model-serving
chmod +x scripts/model-stack scripts/healthcheck scripts/install-open-webui
cp -n .env.example .env
chmod 600 .env
```

预期：命令无报错。若 `.env` 已存在，`cp -n` 不会覆盖它。

## 3. 手动安装并启动 Open WebUI

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/install-open-webui
```

首次运行需要拉取 `ghcr.io/open-webui/open-webui:v0.11.0`，耗时取决于网络。关键输出应包含：

```text
[1/5] Checking the DGX host and project files
[2/5] Pulling the pinned Open WebUI image
[3/5] Starting Open WebUI
[4/5] Waiting for Open WebUI health
Open WebUI healthy: http://100.119.145.78:12000
[5/5] Checking the two declared model routes
Declared models: qwen3.6-35b-a3b-nvfp4, gpt-oss-120b

Open WebUI installation completed.
URL: http://spark-8a8d:12000
```

如果拉取 GHCR 镜像失败，只修改 `.env` 中的 `OPEN_WEBUI_IMAGE` 为你能够访问、且内容对应官方 `v0.11.0` 的镜像地址，然后重新运行安装脚本；不要修改 Compose 文件。

## 4. 创建本地管理员

在已经加入同一 Tailscale 网络的 Windows 电脑打开：

```text
http://spark-8a8d:12000
```

创建第一个账号。第一个账号是管理员；创建完成后进入 `Admin Panel -> Settings -> General`，确认注册开关已关闭。不要关闭登录认证。

Open WebUI 数据保存在：

```text
/home/whtc/workspace/projects/model-serving/artifacts/open-webui
```

重建容器不会删除账号和聊天记录。

## 5. 使用 Qwen

在 DGX 运行：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack switch daily
```

关键输出应类似：

```text
heavy services stopped; MemAvailable=... GiB
... model-serving-qwen ... Started
{"object":"list","data":[{"id":"qwen3.6-35b-a3b-nvfp4",...}]}
Open WebUI healthy: http://100.119.145.78:12000
```

然后在 Open WebUI 新建聊天，选择 `qwen3.6-35b-a3b-nvfp4`，输入：

```text
用中文回答：12乘以17是多少？只给结果和一句解释。
```

预期回答包含 `204`。

也可先在 DGX 直接验收后端：

```bash
python3 tests/smoke.py qwen
```

输出 JSON 的 `model` 应为 `qwen3.6-35b-a3b-nvfp4`，回答内容应包含 `204`。

## 6. 切换到 GPT-OSS-120B

先停止当前聊天生成，再在 DGX 运行：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack switch deep-reasoning
```

关键输出应类似：

```text
heavy services stopped; MemAvailable=... GiB
... model-serving-gpt-oss ... Started
{"object":"list","data":[{"id":"gpt-oss-120b",...}]}
Open WebUI healthy: http://100.119.145.78:12000
```

Open WebUI 不会重启，账号、聊天和文件仍在。在新聊天中选择 `gpt-oss-120b`，使用同一测试问题，预期回答包含 `204`。

直接验收后端：

```bash
python3 tests/smoke.py gpt --reasoning-effort high
```

输出 JSON 的 `model` 应为 `gpt-oss-120b`，回答内容应包含 `204`。

## 7. 日常命令

```bash
# 查看三个容器、端口和统一内存
scripts/model-stack status

# 只查看 WebUI
scripts/model-stack webui status

# 切换日常模型
scripts/model-stack switch daily

# 切换深度推理模型
scripts/model-stack switch deep-reasoning

# 只停止重型模型，保留 WebUI
scripts/model-stack stop

# 全部停止
scripts/model-stack stop-all

# 查看日志
scripts/model-stack logs qwen
scripts/model-stack logs gpt
scripts/model-stack logs webui
```

正常的 `status` 在任一时刻只应有一个重型模型处于 `Up`：`model-serving-qwen` 或 `model-serving-gpt-oss`。`model-serving-open-webui` 应持续为 `Up (healthy)`。

## 8. 重要行为

- Open WebUI 下拉框始终显示两个模型，这是显式模型路由配置的结果。
- 选择未启动的模型会出现后端连接错误；先运行对应的 `switch` 命令，再发送消息。
- 切换期间不要提交请求。脚本在旧模型退出、可用内存达到 20 GiB 后才加载新模型。
- 所有宿主机端口只绑定 `100.119.145.78`，不要改成未限定地址的 `12000:8080`、`8001:8000` 或 `8002:8000`。
- 不要删除 `artifacts/open-webui`，否则账号、聊天和配置都会丢失。
