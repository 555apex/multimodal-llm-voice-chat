# Road Agent 最新版同步至 DGX 计划与实施方法

## 1. 目标与基线

- 最新业务基线：`multimodal-llm-voice-chat@cf6bcd55ff8d0fee97347bd1a8db532579afc1fd`。
- DGX 迁移分支：`migration/dgx`；迁移前基线 `c8c4cc03a68754cdc32e97d149c4eaf9374f33dc` 已保存在 `backup/migration-dgx-c8c4cc0-20260901`。
- Windows 最新源仓库只读，不修改；DGX 独立副本负责合并、部署和验收。
- DGX 部署目录：`/home/whtc/workspace/projects/road-agent-dgx`。

## 2. 部署拓扑

```text
浏览器 --SSH隧道/Tailscale HTTPS--> Nginx --/api--> Spring Boot
                                                    ├── qwen:8000（本地 vLLM）
                                                    ├── Speech Service
                                                    │    ├── faster-whisper small / CPU int8
                                                    │    └── Qwen3-TTS 0.6B Serena / GPU BF16
                                                    └── 共享 MySQL
```

交通事实由最新版 MySQL Adapter 提供，不再调用高德交通 API。宿主机只监听 `127.0.0.1:18080`；Backend 与 Speech 不发布端口，Speech 仅连接 `internal` 网络并离线加载只读模型。

## 3. 代码整合策略

1. 业务领域、MySQL 交通分析、三级工作流、新指挥大屏、OpenAPI 和业务测试以 `cf6bcd5` 为准。
2. 模型适配器同时保留最新版结构化 JSON 修复和 DGX 的 `enable_thinking=false`、无鉴权、HTTP/1.1、上游错误日志。
3. Spring 装配使用最新版 MySQL 路况/通行能力快照、区域压力、车型特征和资源工作流，同时由 Compose 固定本地 Qwen 地址。
4. Speech 保留 DGX 本地 ASR/TTS 引擎、模型 revision、并发限制和 MP3 契约，并合入最新版交通词汇提示词。
5. 主页面采用最新版 `DigitalHumanPanel`，同时保留 `digital-human-demo.html`、原型测试、动作图片和设计素材。
6. 上传改为远端旧项目归档后完整替换，避免已删除的高德/旧前端文件残留；默认保留 DGX `.env`，仅显式 `-RefreshSecrets` 时更新数据库凭据。

## 4. 固定模型配置

```dotenv
ROADAGENT_MODEL_ENDPOINT=http://qwen:8000/v1/chat/completions
ROADAGENT_MODEL_NAME=qwen3.6-35b-a3b-nvfp4
ROADAGENT_MODEL_AUTH_ENABLED=false
ROADAGENT_MODEL_ENABLE_THINKING=false
ROADAGENT_SPEECH_SERVICE_URL=http://speech-service:8091
SPEECH_ASR_MODEL=small
SPEECH_ASR_DEVICE=cpu
SPEECH_ASR_COMPUTE_TYPE=int8
SPEECH_TTS_VOICE=Serena
SPEECH_TTS_LANGUAGE=Chinese
SPEECH_TTS_DEVICE=cuda:0
SPEECH_TTS_DTYPE=bfloat16
```

`dgx-stack up` 检测到 `model-serving-qwen` 已运行时不执行模型切换或重启，只保证其连接 `dgx-ai` 并等待健康。

## 5. 数据库迁移方法

使用固定 `mysql:8.4.11-oraclelinux9` 官方 ARM64 镜像作为一次性客户端。`db-preflight` 解析 JDBC URL 并只显示 schema，不输出密码；记录 MySQL 版本、授权、现有表、行数、字段和外键。

`db-migrate` 只允许在 Road Agent 停止时运行，并先在 `/home/whtc/workspace/backups/road-agent-db/<UTC时间>` 生成受影响表的一致性结构/数据备份、清单和 SHA-256，再依次执行：

1. `20260728_emergency_dispatch.sql`
2. `20260819_three_level_emergency_workflow.sql`
3. `20260820_emergency_resource_dispatch.sql`
4. `20260820_seed_demo_emergency_resources.sql`
5. `20260820_level3_provincial_decision_metadata.sql`
6. `20260820_verify_emergency_resources.sql`（只读核验）

工具没有执行 `20260819_reset_demo_events.sql` 的代码路径。迁移后必须存在六张新增工作流/资源表、至少 108 条 Demo 资源、零库存约束异常和零活动事件城市缺失。

恢复命令要求备份编号二次确认，会校验备份哈希、删除迁移中新建而迁移前不存在的对象，再恢复迁移前 dump。由于新版增加非空字段，失败时必须同时回滚数据库和应用。

## 6. 标准实施顺序

```text
建立备份分支并合并最新版
  → 本地静态检查与前端测试
  → 同步代码（保留密钥）
  → DGX 容器化全量测试与构建
  → 数据库预检
  → 进入维护窗口，仅停止 Road Agent
  → 自动备份、迁移和核验 MySQL
  → 启动新版三个容器
  → Qwen/语音/交通/工作流/Agent 端到端验收
  → 30 分钟稳定性检查
  → 更新验收记录并提交迁移分支
```

统一入口：

```bash
deploy/dgx/dgx-stack preflight
deploy/dgx/dgx-stack build
deploy/dgx/dgx-stack db-preflight
deploy/dgx/dgx-stack down
deploy/dgx/dgx-stack db-migrate
deploy/dgx/dgx-stack db-verify
deploy/dgx/dgx-stack up
deploy/dgx/dgx-stack status
deploy/dgx/dgx-stack smoke --with-tts --with-traffic --with-workflow --with-agent
```

## 7. 验收与回滚准则

- Java 全模块测试、前端 Vitest/双入口构建、Speech 单元测试全部通过。
- Qwen 普通、SSE、JSON 修复以及交通意图/摘要、资源需求/调度方案连续结构化输出通过，所有 DGX 请求无外部 API Key 且关闭 thinking。
- 四类 MySQL 交通结果均返回 `source=MYSQL`；三级待办、历史和详情接口正常。
- Serena MP3、TTS 排队和 TTS→ASR 闭环通过；Speech 运行期无外网。
- 唯一应用监听为 `127.0.0.1:18080`；Qwen/Open WebUI 在切换前后 StartedAt 不变。
- 同时运行至少 30 分钟，容器重启为零、无 OOM。
- 失败时停止新版应用，使用 `db-restore <备份编号> --confirm=<备份编号>` 恢复数据库，再恢复 `/home/whtc/workspace/backups/road-agent-project` 中的旧项目和旧镜像；不停止或改动共享 Qwen/Open WebUI。
