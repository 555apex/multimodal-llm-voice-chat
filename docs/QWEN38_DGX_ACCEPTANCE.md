# Qwen3.8 DGX 验收记录

日期：2026-09-08。模型切换及本轮验收已完成；Road Agent 当前使用 DGX 本地 `qwen3.8-27b-fp8`。未增加前端模型切换入口，未修改业务代码、数据库结构或 ASR/TTS 模型。

- 固定模型：`Qwen/Qwen3.8-27B-FP8@017b9c7af6b5689d5dd426a76e0bc077eb5ca20a`。
- 本地业务基线：`migration/dgx@a13046a`。DGX 正在使用 `merge-20260908` 镜像，远端业务版本比本地新；已采用模型配置定点修改，保留远端镜像、事件分类配置和发布保护逻辑。
- 配置备份：`/home/whtc/workspace/backups/qwen38-config/20260908T105048Z`。
- 模型下载及哈希复核：完成，81 文件、30,890,049,597 字节，无残留 `.part`。快照清单 SHA-256：`30abbf11f45f90e3a4b689f8e3bee21e4f229d5d29c88d3c55469d8ace5aa92f`。
- 下载器修复：支持 HF 元数据的 `lfs.sha256` 字段；修复零字节元数据文件被误判为已完成断点文件的问题，已补回归测试。
- Python 配置测试：本地及 DGX 最终版本均 8/8 通过，覆盖模型参数分离、哈希校验、零字节文件、配置保留、WebUI 配置备份及模型请求参数。
- DGX Java：在 `road-agent-dgx-test-builder:merge-20260908` 隔离镜像内运行 `bash mvnw -o test`，全模块 BUILD SUCCESS；193 项中 176 通过、17 项外部数据库/真实模型集成测试按条件跳过。
- Speech：挂载 DGX 当前 `app` 和 `tests` 到离线测试容器，6/6 通过。
- 本地前端：80/80 测试通过，TypeScript 和 Vite 双入口生产构建通过；未部署或修改前端代码。
- DGX 基础/公网 Compose：配置校验通过。
- 候选 FP8 实机验证：通过。vLLM 0.27.1，文本模式、FP8、65,536 上下文；权重内存 27.64 GiB，加载约 202 秒、首轮编译约 75 秒。健康、模型名、普通响应、JSON、SSE、并发2全部通过。候选业务检查：交通意图、交通摘要、资源需求、调度方案各1轮通过，整组约60.53秒。
- 正式模型和 Backend 切换：完成。`127.0.0.1:8001` 服务模型为 `qwen3.8-27b-fp8`；基础/公网 Backend 均为无鉴权、非思考模式。Backend 使用原 `merge-20260908` 镜像 ID，未重新构建业务镜像。
- Backend 环境备份：`/home/whtc/workspace/backups/road-agent-model/20260908T113222Z`；Frontend、Speech 容器及启动时间保持不变，Nginx 仅刷新上游地址。
- Open WebUI：持久化的 API 模型列表、置顶模型、模型排序已更新；通过现有镜像内的离线工具处理其 root 所有的配置文件，已备份，仅更新模型名称。Open WebUI 和 GPT-OSS 保持原停止状态。
- 100 次顺序 JSON 请求、交通意图/摘要/资源需求/调度方案各20轮：已完成，无结构错误；资源需求与调度方案来自同一响应、分别校验，每轮共3次业务模型请求。并发2候选测试已通过。原长测脚本在完成模型阶段后因旧版跨区域 DTO 断言失败，故没有产生完整 JSON 报告；保留原日志及其 SHA-256，在修正测试断言后单独重跑应用验收，不伪造原测试耗时。
- 真实本地 TTS→ASR：通过。Serena MP3 78,381字节，可解码为6.528秒音频；ASR 回读为“语音测试，福州市普通国省道路段通行平稳，请注意行车安全”，Speech 仅连接内部 Docker 网络。
- Agent SSE 和业务只读接口：通过。四类查询均为 `source=MYSQL`：全省态势45条路线、通行能力47行、跨区域5组城市对/10条通道、车型特征三组各3行；三级待办及历史读取成功。Agent 收到 `answer.delta`、`result.traffic`、`answer.speech` 和 `run.completed`，无 `run.failed`。未执行应急审批、资源调拨或数据库迁移。
- 语音复验：ASR/TTS 能力正常；Serena MP3 32,301字节；并发2请求均成功、整组4.89秒；501字长文本返回预期400。主页面、数字人演示页、公共前端健康检查均为HTTP 200，原有公网鉴权保留。
- 发布保护：远端新增的 `release_ops.py` 原来固定检查 Qwen3.6，现仅将这一断言改为读取运行配置；`guard-live` 与 `dgx-stack preflight` 均通过。保护脚本原件备份于 `qwen38-config/20260908T115152Z`。
- 部署差异审计：402 个业务源文件的 SHA-256 全部一致；两个 Backend 镜像 ID 一致；Frontend、Speech、Open WebUI、GPT-OSS 的容器 ID、启动时间和重启次数保持一致。
- 30 分钟稳定性：通过。北京时间19:34:50至20:04:50，共1800.02秒、61次采样；Qwen、两个 Backend、两个 Frontend、Speech 全程健康，重启次数均为0，无 OOM、容器替换或异常。可用内存最低48.93GiB，末次48.94GiB；语音模型加载及测试预热期间占用有所增加，23:30以后复查仍约48GiB可用、swap使用为0，未见持续明显增长。本结果是有限时段观察，不代表长期无泄漏保证。

## 交付与证据

- 操作入口：[Qwen3.8 部署与模型切换指南](../Qwen3.8_DGX部署与模型切换指南.md)。模型注册表为 `model-serving/configs/models.yaml`；两个服务端 `.env` 通过 `switch` / `model-reload` 更新。
- [候选推理检查](qwen38-evidence/qwen3.8-27b-fp8-candidate.json)、[模型压力测试证据](qwen38-evidence/model-stress-evidence.json)、[应用验收报告](qwen38-evidence/application-smoke.json)。
- [TTS→ASR 回读](qwen38-evidence/speech-roundtrip.json)、[完整稳定性采样](qwen38-evidence/stability.json)、[最终源码与容器比较](qwen38-evidence/final-comparison.json)。
- DGX 原始日志目录：`/home/whtc/workspace/projects/model-serving/artifacts/qwen38-20260908`。首次切换备份：`model-serving/artifacts/model-control/switch-20260908T112401Z`；原始日志中 WebUI 文件权限问题已通过离线配置工具解决，模型无需因此重启。
- 本轮没有把本地旧业务镜像部署到 DGX，没有提交或覆盖原有未提交的公网访问改动、使用文档。
- 范围限制：前端80项为本地副本测试，远端前端采用页面HTTP检查和源码/镜像未变审计；17项需外部服务的 Java 集成测试跳过。JSON 修复逻辑由现有 Java 单元测试覆盖，未在生产端刻意注入错误模型响应或执行真实业务写入来触发重试。
