# Qwen3.8 DGX 部署与模型切换指南

本次部署使用 `Qwen/Qwen3.8-27B-FP8`，模型服务和模型切换均在 DGX 服务端管理，Road Agent 前端不增加模型选择功能。

2026-09-08 已完成上线：100次顺序结构化请求、四类业务各20轮、Agent/交通/语音验收及30分钟稳定性检查通过。原 Qwen3.6 权重保留，可按下文回滚。

## 模型与部署参数

官方模型为 Apache-2.0 许可的 27B 稠密 FP8 模型，支持文本和视觉输入，原生上下文 262,144；本项目仅使用文本和 65,536 上下文，保留非思考模式。模型量化方法及许可见[官方模型页](https://huggingface.co/Qwen/Qwen3.8-27B-FP8)，vLLM 部署参数参考[官方配方](https://recipes.vllm.ai/Qwen/Qwen3.8-27B)。

| 项目 | 配置 |
| --- | --- |
| 权重目录 | `/home/whtc/models/Qwen--Qwen3.8-27B-FP8` |
| 固定 revision | `017b9c7af6b5689d5dd426a76e0bc077eb5ca20a` |
| 快照大小 | 81 文件，30,890,049,597 字节 |
| API 模型名 | `qwen3.8-27b-fp8` |
| Road Agent 内网地址 | `http://qwen:8000/v1/chat/completions` |
| DGX 本机地址 | `http://127.0.0.1:8001/v1` |
| 候选测试地址 | `http://127.0.0.1:18001/v1` |
| 推理镜像 | vLLM 0.27.1 ARM64，镜像 digest 固定在模型注册表 |

FP8 权重按固定版本下载并校验 SHA-256，以只读目录挂载。默认并发 2、GPU 内存占比 0.50、FP8 KV cache。Qwen3.8 初次上线不启用 MTP；Qwen3.6 回滚配置仍保留原有 MoE/MTP 参数。稠密模型的响应速度需以本机实测为准，参数量较小不代表一定比原 MoE 模型更快。

## 日常启动与使用

在 DGX 终端执行：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
```

DGX 浏览器打开 `http://127.0.0.1:18080`。本次模型切换保留已有页面和公网设置。

Windows 使用 PowerShell 开通 SSH 隧道，终端保持打开：

```powershell
ssh -i "$env:USERPROFILE\.ssh\id_ed25519_dgx_spark" -N -L 18080:127.0.0.1:18080 -L 8001:127.0.0.1:8001 whtc@100.119.145.78
```

Windows 浏览器访问 `http://127.0.0.1:18080`。模型调试接口为 `http://127.0.0.1:8001/v1/models`；不再直连 `100.119.145.78:8001`。结束隧道按 `Ctrl+C`，DGX 服务继续运行。

## 以后在哪个文件中切换模型

无需修改 Java 或前端代码。相关文件如下：

| DGX 文件 | 用途 |
| --- | --- |
| `/home/whtc/workspace/projects/model-serving/configs/models.yaml` | 模型注册表：下载源、revision、目录、别名和对应 vLLM 参数；新增其他模型时在这里登记 |
| `/home/whtc/workspace/projects/model-serving/.env` | 当前实际加载的权重目录、API 模型名和端口绑定 |
| `/home/whtc/workspace/projects/road-agent-dgx/deploy/dgx/.env` | Road Agent 选择的 API 模型名和地址；包含其他私密配置，权限保持 600 |
| `road-agent-dgx/deploy/dgx/compose.yaml`、`compose.public.yaml` | 从 `.env` 装配基础及公网 Backend，已去除旧模型名硬编码 |

Windows 对应目录为 `C:\Users\Lenovo\Desktop\DGX_S534\model-serving` 和 `C:\Users\Lenovo\Desktop\DGX_S534\multimodal-llm-voice-chat-dgx`。Windows 文件修改后仍需同步到 DGX 才能影响部署；真实 `.env` 以 DGX 为准。

本次采用模型配置定点同步：DGX 业务镜像为 `merge-20260908`，高于本机该副本的业务基线。日常换模型直接使用以下 DGX 命令，避免用旧业务副本全量部署覆盖当前版本。远端 `deploy/dgx/release_ops.py` 的模型校验也已改为读取 `.env`，无需每次修改旧模型名称常量。

### 推荐：命令切换

切回旧 Qwen3.6：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack switch qwen36

cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack model-reload --model qwen3.6-35b-a3b-nvfp4
deploy/dgx/dgx-stack smoke
```

切到已通过候选验证的 Qwen3.8：

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack switch qwen38

cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack model-reload --model qwen3.8-27b-fp8
deploy/dgx/dgx-stack smoke
```

`switch` 选择注册表中的正确推理参数，健康检查通过后更新模型服务 `.env`。`model-reload` 确认目标模型已可用，更新 Road Agent 模型配置，仅重新创建当前运行的 Backend，并保留其原镜像 ID。Frontend 会刷新 Nginx 的上游地址，但容器不会重启；Speech、数据库和模型容器不被该命令重建。

### 手工编辑配置

模型服务 `.env` 的三个字段：

```dotenv
QWEN_MODEL_DIR=/home/whtc/models/Qwen--Qwen3.8-27B-FP8
QWEN_SERVED_MODEL_NAME=qwen3.8-27b-fp8
QWEN_HOST_BIND=127.0.0.1
```

Road Agent 的 `deploy/dgx/.env`：

```dotenv
ROADAGENT_MODEL_ENDPOINT=http://qwen:8000/v1/chat/completions
ROADAGENT_MODEL_NAME=qwen3.8-27b-fp8
ROADAGENT_MODEL_AUTH_ENABLED=false
ROADAGENT_MODEL_ENABLE_THINKING=false
```

两个模型名必须完全一致。推荐让上述命令自动更新这两个文件，不要在候选验证通过前把新模型手工设为生产默认。需要手工修改时先保留原 `.env` 副本，失败时按备份恢复配置并显式执行旧模型的 `switch` / `model-reload`。不要直接使用通用 Compose 命令切换权重，否则可能没有选中对应的 MoE/稠密模型参数。

## 下载与候选验证

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/download-models qwen38
scripts/model-stack verify qwen38
```

DGX 单卡不同时加载新旧重模型。候选验证前需在维护窗口显式停止当前重模型：

```bash
scripts/model-stack stop
scripts/model-stack candidate qwen38
scripts/model-stack smoke qwen38 --base http://127.0.0.1:18001 --iterations 100
```

候选模型独立于正式 `qwen` 网络别名。验证失败时不会激活 Qwen3.8，执行 `scripts/model-stack candidate-stop` 后可用 `switch qwen36` 恢复旧模型。成功记录与镜像、模型 revision、启动参数和权重清单哈希绑定；这些内容改变后须重新验证候选。

## 状态、日志与关闭

```bash
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack status
scripts/model-stack logs qwen
scripts/model-stack config daily
scripts/model-stack verify daily --quick
```

关闭应用，再关闭重模型：

```bash
cd /home/whtc/workspace/projects/road-agent-dgx
deploy/dgx/dgx-stack down
cd /home/whtc/workspace/projects/model-serving
scripts/model-stack stop
```

Open WebUI 原来处于停止状态时，模型切换会保留该状态。需要时单独运行 `scripts/model-stack webui start`。

## 备份与验收记录

模型配置变更备份位于 `/home/whtc/workspace/backups/qwen38-config/`；Backend 配置刷新备份位于 `/home/whtc/workspace/backups/road-agent-model/`。包含 `.env` 的备份权限为 600，不应上传到 Git 或共享。

详细执行结果见[验收记录](docs/QWEN38_DGX_ACCEPTANCE.md)。Qwen3.6 原权重和原参数保留，回滚不涉及数据库。
