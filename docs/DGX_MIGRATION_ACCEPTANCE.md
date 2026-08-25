# DGX Road Agent 迁移验收记录

- 执行日期：2026-08-25
- 执行人：Codex（与 DGX 用户 `whtc` 协作）
- 迁移分支：`migration/dgx`
- 部署目录：`/home/whtc/workspace/projects/road-agent-dgx`

## 验收结论

Road Agent 已在 DGX Spark 上完成构建和部署，Qwen Agent、共享 MySQL、高德、离线 ASR、离线 Serena TTS、前端主页面和数字人演示页均可工作。容器与网络安全检查通过。

以下项目需要外部权限或现场输入，暂不计为自动验收失败：

- tailnet 管理员尚未启用 Tailscale Serve，控制面授权后需重跑 `deploy/dgx/dgx-stack serve`。
- 浏览器麦克风权限需要在最终 Tailscale HTTPS 页面人工确认。
- 仓库没有福建道路现场录音；已用本地 Serena 生成的中文道路语音完成真实 ASR/TTS 闭环，仍建议补测一段现场录音。
- 共享数据库写入式调度验收会审批真实待处理告警，未获得具体事件的明确授权；当前只完成只读告警查询和全量调度自动测试。

## 固定版本

| 项目 | 固定值 | 实测/摘要 |
|---|---|---|
| DGX | GB10 / ARM64 | `spark-8a8d`，约 121.7 GiB 统一内存 |
| Qwen | `qwen3.6-35b-a3b-nvfp4` | revision `491c2f1ea524c639598bf8fa787a93fed5a6fbce`；vLLM `/v1/models` 实测一致 |
| vLLM | 现有 model-serving | vLLM `0.27.1`；镜像 digest `sha256:1c8e60a0841b333c700488cb029d3664807249da0c071e862191b00fe34b228c` |
| ASR | `Systran/faster-whisper-small` | revision `536b0662742c02347bc0e980a01041f333bce120`，CPU/int8 |
| TTS | `Qwen3-TTS-12Hz-0.6B-CustomVoice` | revision `f3d1af06e4eaefac12b1ffa6726f9eef674a6f02` |
| TTS 声音 | Serena / Chinese / BF16 | GPU `cuda:0`，SDPA，最大并发 1 |
| Speech 基础镜像 | NVIDIA PyTorch 25.08 ARM64 | digest `sha256:d724ba5b68075cd3b96eefbc510a45d36e60dd16fd70b217708625f1f4b37bc1` |

## 自动测试与构建

| 检查 | 结果 | 证据摘要 |
|---|---|---|
| Java 全量测试 | 通过 | 7 个 Maven reactor 模块 `BUILD SUCCESS`；包含模型名、无鉴权、thinking、JSON 修复及 SSE 测试 |
| 前端 Vitest | 通过 | 14 个测试文件、36 个测试全部通过 |
| 前端生产构建 | 通过 | 同时生成 `index.html` 和 `digital-human-demo.html` |
| Speech 单元测试 | 通过 | 6 个测试全部通过，覆盖替身、路由、模型路径和 MP3 转码 |
| 模型 manifest 校验 | 通过 | ASR/TTS 固定 revision 的全部 manifest 文件离线重算 SHA-256 通过 |
| Docker Compose/镜像 | 通过 | Backend、Frontend、Speech 三个 ARM64 镜像构建完成并健康运行 |
| 幂等重复启动 | 通过 | Qwen 已运行时，`dgx-stack up` 保持原模型进程；重复执行前后容器 `StartedAt` 均为 `2026-08-25T15:11:00.940479943Z`，重启次数为 0 |
| Git 内容检查 | 通过 | 迁移分支 `migration/dgx`；原仓库仍为 `version/roadagent-v1@18074e8`，原工作树内容未被迁移任务改写 |

## DGX 端到端

| 检查 | 结果 | 证据摘要 |
|---|---|---|
| Qwen 普通响应 | 通过 | 模型名正确，普通中文回答有效 |
| Qwen SSE | 通过 | 收到增量内容及 `[DONE]` |
| Qwen 严格 JSON | 通过 | 20/20 和 100/100 次 `response_format=json_object` 均可解析；Agent 形状结构化请求通过 |
| 业务结构化输出 | 通过 | 交通意图 20/20、应急调度方案 20/20 均包含必需字段；只调用模型，未写数据库；用时 186.61 秒 |
| Java → Qwen | 通过 | 固定 HTTP/1.1 后，解决 vLLM/Uvicorn h2c 导致 POST body 丢失的问题 |
| 后端连接共享 MySQL | 通过（只读） | 连接池建立成功；读取到 17 条待处理告警，首条事件 `202607280000000017` |
| 高德实时路况 | 通过 | 福州五四路返回 `AMAP` 事实、4 个路段；Agent 只基于这些路段生成结论 |
| Agent 业务 SSE | 通过 | 完整收到意图、Skill、Tool、回答、路况结果和完成等 12 个事件 |
| ASR 中文道路语音 | 通过（合成样本） | Serena MP3 回灌 faster-whisper，识别为“当前道路通行平稳”，语言 `zh`，时长 2304 ms |
| TTS 本地 MP3 | 通过 | Serena 返回 27,693 字节 `audio/mpeg`，FFprobe 识别为 MP3 |
| TTS 并发排队 | 通过 | 2 个并发请求均成功，输出 51,597/43,821 字节，总耗时 5.56 秒；服务最大并发为 1 |
| TTS 长文本限制 | 通过 | 501 字请求被应用以 HTTP 400 拒绝，未进入模型推理 |
| Speech 无外网运行 | 通过 | 仅连接 `internal=true` 网络、无宿主机端口；`HF_HUB_OFFLINE=1`、`TRANSFORMERS_OFFLINE=1`；真实 ASR/TTS 成功 |
| Speech 降级/恢复 | 通过 | 停止后能力接口返回不可用，Qwen/高德/文字 SSE 仍完成；重启后 ASR/TTS 自动恢复健康 |
| 100 次模型请求 | 通过 | 100 次连续结构化请求用时 20.52 秒，无 JSON 错误 |
| Tailscale HTTPS/麦克风 | 待外部授权 | tailnet 尚未启用 Serve；当前 `No serve config`，没有残留半配置路由；管理员授权地址见“待完成项” |
| 30 分钟稳定性 | 通过 | 2026-08-25 20:31:34—21:05:32 +08:00 连续并行运行 33 分 58 秒；Backend、Frontend、Speech、Qwen 均为 healthy，重启次数均为 0、`OOMKilled=false`；窗口结束后模型、TTS、高德和 Agent SSE 冒烟再次通过 |

## 容器与安全证据

| 镜像 | Image ID | 大小 |
|---|---|---:|
| Backend | `sha256:068fdeba8e621b874b96f3eaec5e9da21d7fb783b5d90b438309fa83e58de311` | 285,362,077 bytes |
| Frontend | `sha256:428ada4df5158ac5531089561ef2de3a311ab457c12ee1bbd69039acdf5915b5` | 57,972,444 bytes |
| Speech | `sha256:1c19ffeac0a6b9326acc7b125805f1cc906ddc651166d72dca442e24086f4938` | 22,821,139,031 bytes |

- 新应用唯一宿主机监听为 `127.0.0.1:18080`；Backend 和 Speech 没有端口绑定。
- Speech 只在 `road-agent-dgx_speech-private` 网络中，网络 `Internal=true` 且没有网关。
- 模型目录只读挂载；运行时禁用 Hugging Face 自动联网和遥测。
- 真实密钥仅在远端 `deploy/dgx/.env`，权限 `600`，未进入 Git、镜像或验收输出。
- 稳定性窗口结束时容器内存约：Backend 403 MiB、Frontend 17 MiB、Speech 2.3 GiB、Qwen 11.6 GiB；GPU 进程记录 Qwen 48,220 MiB、Speech Python 2,417 MiB；DGX 总可用统一内存约 121.7 GiB。

## 已处理的 DGX 兼容性问题

1. NVIDIA PyTorch 26.02 要求 590 系列驱动，改为固定 ARM64 25.08 digest，兼容当前 580 驱动。
2. PyPI torchaudio 与 NVIDIA 预览版 PyTorch ABI 不一致；Serena 不使用声纹克隆，故将仅供声纹克隆的 torchaudio 导入改为惰性加载。
3. Java `HttpClient` 默认 h2c 升级会被 vLLM/Uvicorn 拒绝并丢失 POST body；模型客户端现固定 HTTP/1.1。
4. Frontend 增加独立 ingress 网络后，宿主机回环端口可以发布，同时 Backend/Speech 仍不暴露。
5. 初版重复执行 `up` 会无条件切换 `daily` profile 并短暂重启健康的 Qwen；现仅在 Qwen 容器未运行时切换，已运行时只等待健康并验证模型，避免页面出现瞬时 `MODEL_UPSTREAM_ERROR`。

## 待完成项

1. tailnet 管理员访问 `https://login.tailscale.com/f/serve?node=ntRhLsbfUN11CNTRL` 启用 Serve，然后执行：

   ```bash
   deploy/dgx/dgx-stack serve
   ```

2. 在生成的 HTTPS 地址打开主页面和数字人演示页，授权麦克风并录制“福州五四路现在拥堵吗”。
3. 如需执行告警 `202607280000000017` 的生成→驳回→重生成→审批写入验收，请先取得业务负责人明确授权。

## 回滚基线

- 原 Windows 项目未修改；共享 MySQL 未执行 schema、建表或数据迁移。
- model-serving 原始 Compose 备份：`/home/whtc/workspace/projects/model-serving/docker/compose.yaml.pre-road-agent-20260825`。
- model-serving 原始 SHA-256：`fa81788a8b3fee90895a110aeb92d2f27123065b111d7e15d5c228d2da2262c9`；附加 `dgx-ai` 后为 `272db69123251534731aa9c19bbd24e58e055703023ef270bc3f90263ff10b51`。
- 回滚命令：`deploy/dgx/dgx-stack down`，随后按需执行 `deploy/dgx/dgx-stack serve-off`。
- `down` 不停止 Qwen/Open WebUI，不删除模型，不改共享 MySQL。
