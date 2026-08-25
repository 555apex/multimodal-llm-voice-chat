# Road Agent 迁移至 DGX Spark 计划书与实施方法

## 目标

在不改变交通、应急调度、前端、数据库和高德接口契约的前提下，将 Road Agent 完整部署到 DGX Spark：Agent 大模型改为 DGX 上现有的 `Qwen3.6-35B-A3B-NVFP4`，ASR 保持本地 `faster-whisper small/CPU/int8`，TTS 改为本地 `Qwen3-TTS-12Hz-0.6B-CustomVoice` Serena 中文女声。

## 部署拓扑

```text
浏览器 --Tailscale HTTPS--> Nginx 前端 --/api--> Spring Boot
                                                |-- qwen:8000 (vLLM)
                                                |-- speech-service:8091
                                                |     |-- faster-whisper small / CPU
                                                |     `-- Qwen3-TTS 0.6B / GPU
                                                |-- 外部共享 MySQL
                                                `-- 高德 Web API
```

宿主机仅监听 `127.0.0.1:18080`，由 Tailscale Serve 提供 tailnet 内 HTTPS。Java、Speech 和 Qwen 通过 Docker 私有网络通信；Speech 所在网络设为 `internal`，运行期不能访问外网。

## 实施方法

1. 在 `multimodal-llm-voice-chat-dgx` 独立副本的 `migration/dgx` 分支实施，原始项目保持不变。
2. Java 的 OpenAI-compatible 适配器增加可选 `ROADAGENT_MODEL_ENABLE_THINKING`；DGX 设为 `false`，其他环境未配置时不改变请求体。
3. 复用 `/home/whtc/workspace/projects/model-serving` 中的 Qwen 服务，并把 Qwen 容器附加到外部网络 `dgx-ai`。
4. Speech 服务使用固定 revision 的 ASR/TTS 权重，只读挂载 `/home/whtc/models`；TTS 生成 WAV 后使用 FFmpeg 转为 MP3，保持现有 REST 和 MIME 类型。
5. 使用 ARM64 多阶段镜像构建 Spring Boot、Vue/Nginx 和 Speech 服务，真实密钥仅保存在 DGX 的 `deploy/dgx/.env`，权限为 `600`。
6. 使用 `deploy/dgx/dgx-stack` 完成预检、构建、启动、冒烟、状态、日志和回滚。

## DGX 实施中的兼容性处理

- Speech 基础镜像固定为 ARM64 的 NVIDIA PyTorch `25.08-py3` manifest digest。`26.02` 要求 590 系列驱动，与 DGX 当前 580 驱动不兼容。
- Qwen3-TTS 的预置 Serena 路径不使用声纹克隆模块；镜像将仅供声纹克隆使用的 `torchaudio` 导入改为惰性加载，避免 NVIDIA 预览版 PyTorch 与 PyPI torchaudio 的 ABI 冲突。
- TTS 使用 PyTorch SDPA、GPU BF16；ASR 仍使用 CPU int8，两个模型均从只读本地目录离线加载。
- Java `HttpClient` 固定为 HTTP/1.1，避免向 vLLM/Uvicorn 明文端点发起不受支持的 h2c 升级而丢失 POST 请求体。HTTPS/OpenAI-compatible 接口仍可按原契约调用。

## 固定配置

```dotenv
ROADAGENT_MODEL_ENDPOINT=http://qwen:8000/v1/chat/completions
ROADAGENT_MODEL_NAME=qwen3.6-35b-a3b-nvfp4
ROADAGENT_MODEL_AUTH_ENABLED=false
ROADAGENT_MODEL_ENABLE_THINKING=false
ROADAGENT_SPEECH_SERVICE_URL=http://speech-service:8091
SPEECH_ASR_MODEL=small
SPEECH_ASR_DEVICE=cpu
SPEECH_ASR_COMPUTE_TYPE=int8
SPEECH_TTS_LANGUAGE=Chinese
SPEECH_TTS_VOICE=Serena
SPEECH_TTS_DEVICE=cuda:0
SPEECH_TTS_DTYPE=bfloat16
```

## 验收与回滚

- Java、前端、Speech 自动测试和生产构建全部通过。
- Qwen 普通、SSE、`response_format=json_object` 三种调用通过，结构化输出连续 20 次有效。
- 真实中文录音可识别，TTS 返回可解码 `audio/mpeg`；阻断 Speech 外网后语音仍正常。
- 高德查询和共享 MySQL 告警/调度流程保持原行为。
- 同时运行 30 分钟且执行 100 次顺序模型请求，无 OOM 或容器重启。
- 回滚只需执行 `deploy/dgx/dgx-stack down`、关闭对应 Tailscale Serve 路由并恢复 model-serving Compose；不涉及数据库回滚。
