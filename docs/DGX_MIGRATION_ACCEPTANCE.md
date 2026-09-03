# Road Agent 最新版同步至 DGX 验收记录

- 执行窗口：2026-09-01—2026-09-02（Asia/Shanghai）
- 迁移分支：`migration/dgx`
- 最新业务基线：`cf6bcd55ff8d0fee97347bd1a8db532579afc1fd`
- DGX 迁移前基线：`c8c4cc03a68754cdc32e97d149c4eaf9374f33dc`
- 备份分支：`backup/migration-dgx-c8c4cc0-20260901`
- DGX 部署目录：`/home/whtc/workspace/projects/road-agent-dgx`

## 1. 验收结论

最新版 Road Agent 已同步、构建并切换到 DGX Spark。新版 MySQL 交通分析、三级应急工作流、新指挥大屏及配套接口已经启用；原有数字人演示页和素材保留。

模型链路保持 DGX 本地化：Agent 使用现有 `Qwen3.6-35B-A3B-NVFP4`，ASR 使用本地 faster-whisper small/CPU/int8，TTS 使用本地 Qwen3-TTS 0.6B Serena/GPU BF16。Qwen 与 Open WebUI 在应用切换期间没有停止或重启。

除最终 30 分钟稳定性终点采样按用户要求暂缓外，已执行的自动化验收均通过。仍需现场完成的项目包括：30 分钟终点采样、tailnet 管理员启用 Tailscale Serve 后的 HTTPS 浏览器麦克风授权，以及使用真实福建道路录音补测 ASR；当前可通过 Windows SSH 隧道查看和测试系统。

## 2. 固定配置

| 项目 | 固定值 | 实测结果 |
|---|---|---|
| DGX | NVIDIA GB10 / ARM64 | 约 121 GiB 统一内存，约 3.2 TiB 可用磁盘 |
| Agent 模型 | `qwen3.6-35b-a3b-nvfp4` | `http://qwen:8000/v1/chat/completions`，无鉴权，`enable_thinking=false`，HTTP/1.1 |
| Qwen 权重 | Qwen3.6-35B-A3B-NVFP4 | revision `491c2f1ea524c639598bf8fa787a93fed5a6fbce` |
| ASR | `Systran/faster-whisper-small` | revision `536b0662742c02347bc0e980a01041f333bce120`，CPU/int8，离线只读挂载 |
| TTS | `Qwen3-TTS-12Hz-0.6B-CustomVoice` | revision `f3d1af06e4eaefac12b1ffa6726f9eef674a6f02`，Serena/Chinese/BF16/cuda:0，最大并发 1 |
| 交通事实源 | 共享 MySQL | 高德交通 Adapter 已移除；四类响应均实测 `source=MYSQL` |
| 对外入口 | Nginx | 仅 `127.0.0.1:18080`，Backend/Speech 无宿主机端口 |

## 3. 代码同步与构建

| 检查 | 结果 | 证据摘要 |
|---|---|---|
| 最新业务合入 | 通过 | 合入 `cf6bcd5` 的 MySQL 交通、三级工作流、新前端、文档和 OpenAPI |
| 最新源仓库保护 | 通过 | Windows 源仓库仍为 `version/roadagent-v1@cf6bcd5`，仅保留迁移前已存在的 `mvnw.cmd` 本地修改，本任务未写入源仓库 |
| DGX 能力保留 | 通过 | 本地 Qwen、thinking 关闭、HTTP/1.1、离线 ASR/TTS、ARM64 Compose 和运维脚本均保留 |
| 数字人资产 | 通过 | 主大屏与独立 `digital-human-demo.html` 均采用共享“路智通”三姿态渲染器；原女性资产保留用于回滚 |
| Java 全量测试 | 通过 | 7 个 Maven reactor 模块 `BUILD SUCCESS`；150 项测试，0 失败，4 项需真实模型的兼容测试按设计跳过 |
| 共享 MySQL 集成测试 | 通过 | 四类交通只读集成测试及数据库连接测试通过；三级工作流 2 项事务测试完成后自动回滚并校验前后数据库状态一致 |
| 前端 Vitest | 通过 | 17 个测试文件、71 项测试全部通过（原有 59 项保持通过） |
| 前端生产构建 | 通过 | 同时生成主指挥大屏与独立数字人演示页 |
| Speech 单元测试 | 通过 | 6 项测试全部通过，覆盖替身、模型路径、路由和 MP3 转码 |
| 语音模型 manifest | 通过 | 在 `--network none` 容器内按 manifest 离线重算全部文件 SHA-256，ASR/TTS 的固定 repo 与 revision 均验证成功 |
| Docker 构建 | 通过 | Backend、Frontend、Speech 三个 ARM64 镜像构建成功并健康运行 |

## 4. 数据库迁移

### 4.1 预检与备份

- MySQL 服务版本：`8.0.46`；目标 schema：`road_agent`。
- 迁移账号具备所需权限，预检过程未输出密码。
- 固定 MySQL 客户端镜像：`dockerproxy.net/library/mysql:8.4.11-oraclelinux9`。
- 客户端镜像 ID：`sha256:5e7e005a680e75d935984d3d9390990d2a709b3ed67e92708e9e6747f1f754c9`。
- 客户端 RepoDigest：`sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`。
- MySQL 查询、SQL 文件执行、备份和恢复均显式固定 `utf8mb4`；库内“福州/医疗救护车”等中文原始值和客户端显示均已复核正常。
- 数据库备份 ID：`20260901T151728Z`。
- 备份目录：`/home/whtc/workspace/backups/road-agent-db/20260901T151728Z`。
- 迁移前逻辑备份：`pre-migration.sql`，172,253 bytes，SHA-256 `096e1db00eb33487687956817d93eaff3e06805a98d8a69dbff70c98910e36fe`。
- 备份同时保存迁移前表清单、精确行数、字段、外键、SQL 哈希、镜像 ID、执行日志和 `SHA256SUMS`。

### 4.2 执行结果

迁移按以下固定顺序完成：

1. `20260728_emergency_dispatch.sql`
2. `20260819_three_level_emergency_workflow.sql`
3. `20260820_emergency_resource_dispatch.sql`
4. `20260820_seed_demo_emergency_resources.sql`
5. `20260820_level3_provincial_decision_metadata.sql`
6. `20260820_verify_emergency_resources.sql`

`20260819_reset_demo_events.sql` 未执行。共享库在本次切换前已经包含部分新版对象和 Demo 数据，迁移脚本以幂等方式完成，未重复插入或破坏既有记录。

迁移后校验：

| 项目 | 结果 |
|---|---:|
| 必需的三级工作流/资源表 | 6 张，完整 |
| Demo 资源池 | 108 条 |
| 总量/可用/预留/已调度 | 592 / 506 / 56 / 30 |
| 非法库存约束记录 | 0 |
| 未回填城市的活动 Demo 事件 | 0 |

## 5. DGX 端到端验收

| 检查 | 结果 | 证据摘要 |
|---|---|---|
| Qwen 模型与配置 | 通过 | `/v1/models` 返回固定模型名；Backend 环境为本地 `qwen:8000`、无鉴权、thinking=false，不含外部模型 API Key |
| Qwen 普通响应 | 通过 | OpenAI-compatible 普通响应有效 |
| Qwen SSE | 通过 | 收到增量内容及 `[DONE]` |
| Qwen 严格 JSON | 通过 | 连续 100/100 次 `response_format=json_object` 可解析 |
| 业务结构化输出 | 通过 | 交通意图、交通摘要、资源需求、调度方案各 20/20 次无结构错误；整批 162 次调用用时 121.47 秒 |
| MySQL 交通四类查询 | 通过 | 路况、通行能力、区域压力、车型特征均返回 `source=MYSQL`；实测数据行数分别为路段快照、40、35、9（按各子列表合计） |
| 三级工作流只读接口 | 通过 | LEVEL_1/2/3 待办计数和历史分页结构正常 |
| Agent 业务 SSE | 通过 | 收到 `run.started`、意图、Skill、Tool、`answer.delta`、`result.traffic`、语音文本和完成等 12 个事件 |
| Windows 页面入口 | 通过 | SSH 隧道下主指挥大屏和数字人演示页均为 HTTP 200，两页引用的 CSS/JS/图标资源全部加载成功 |
| TTS 本地 MP3 | 通过 | Serena 返回 28,557 bytes `audio/mpeg`；断网容器内 FFprobe 确认为 2.376 秒可解码 MP3 |
| TTS 并发排队 | 通过 | 2 个并发请求均成功，49,581/50,733 bytes，总耗时 4.88 秒；服务最大并发为 1 |
| TTS 长文本限制 | 通过 | 501 字请求返回 HTTP 400，未进入模型推理 |
| TTS→ASR 闭环 | 通过 | Serena MP3 回灌 faster-whisper，识别为“当前道路通行平稳”，语言 `zh`，音频时长 2,376 ms |
| Speech 离线隔离 | 通过 | 仅连接 `internal=true` 私有网络；`HF_HUB_OFFLINE=1`、`TRANSFORMERS_OFFLINE=1`；模型目录只读挂载；真实 ASR/TTS 成功 |
| Speech 降级/恢复 | 通过 | Speech 停止时能力接口显示不可用，Qwen、四类 MySQL 交通和文字 Agent SSE 继续工作；恢复后重新变为 healthy |
| 30 分钟稳定性 | 暂缓 | 按用户要求不再等待终点；已观察至 `2026-09-01T17:00:54Z`（约 25 分 16 秒），Backend、Frontend、Speech、Qwen 均 healthy、重启 0、`OOMKilled=false`，本次不据此标记 30 分钟验收通过 |

## 6. 容器、安全与不影响现有模型的证据

| 镜像 | Image ID | 大小 |
|---|---|---:|
| Backend | `sha256:91d9a1e68e58c1a758d37641b723610133fac0eac4aa7d48b862b724382fda0a` | 284,306,831 bytes |
| Frontend | `sha256:594c779f1ed7aa8517a63d0e1dcc0fcde345ba1e3008c3c150442ff34b9b70e9` | 70,120,651 bytes |
| Speech | `sha256:44839c8b35f080e560cb024d10dfb9189064878770c5583026bd543062cf7b24` | 22,821,207,612 bytes |

- Qwen `StartedAt=2026-09-02T09:29:26.953996084Z`，重启次数 0，`OOMKilled=false`；卡通数字人升级前后该时间戳保持不变。
- Open WebUI `StartedAt=2026-09-02T07:16:50.90801936Z`，重启次数 0；卡通数字人升级未影响其运行。
- `dgx-stack up` 明确输出“`model-serving-qwen is already running; keeping the current local model process`”，未切换、未重启模型服务。
- 新应用唯一宿主机监听是 `127.0.0.1:18080`；Backend 和 Speech 只有容器内部端口。
- Backend、Frontend、Speech 分别以 `roadagent`、`nginx`、`speech` 非 root 用户运行。
- Qwen 原有 Tailscale 地址 `100.119.145.78:8001` 和 Open WebUI `100.119.145.78:12000` 保持不变。
- 真实数据库凭据仅存在远端 `deploy/dgx/.env`，权限 `600`，未进入 Git、镜像、日志或验收内容。

## 7. 回滚基线

- 迁移前项目归档：`/home/whtc/workspace/backups/road-agent-project/road-agent-dgx-20260901T151247Z.tar.gz`。
- 旧应用镜像已标记：`road-agent-dgx-backend:pre-cf6bcd5`、`road-agent-dgx-frontend:pre-cf6bcd5`、`road-agent-dgx-speech:pre-cf6bcd5`。
- 旧 Compose SHA-256：`01c0c9e3e261047e5de4de2920e6e4e2ccc6940a30a779fcdf19bd0b46689875`。
- 数据库可使用 `deploy/dgx/dgx-stack db-restore 20260901T151728Z --confirm=20260901T151728Z` 显式恢复；恢复前必须停止 Road Agent。
- 回滚新版必须同时恢复应用归档/旧镜像和数据库备份，不能只回滚应用。
- `deploy/dgx/dgx-stack down` 只停止 Road Agent，不停止 Qwen/Open WebUI，不删除模型。

## 8. 现场待完成项

1. 后续需要正式关闭 30 分钟稳定性验收时，从 Speech 健康状态重新开始连续计时并补录终点状态。
2. tailnet 管理员启用 Tailscale Serve 后执行 `deploy/dgx/dgx-stack serve`，再在 HTTPS 页面授权浏览器麦克风。
3. 在现场页面录制一段福建道路真实语音，补充 ASR 场景验收；当前合成中文道路语音闭环已通过。

## 9. 卡通数字人生产接入（2026-09-03）

| 检查 | 结果 | 证据摘要 |
|---|---|---|
| 人物替换 | 通过 | 主 Agent 已停止使用 `/emergency-agent-avatar.png`，改用“路智通”卡通守护者三姿态资产 |
| 透明资产 | 通过 | 三姿态均为 1024×1536 RGBA；PNG 与无损 WebP 已生成；脚底基线统一为 Y=1518；哈希见资产清单 |
| 六状态映射 | 通过 | idle/listening→待命，thinking→思考，answering/speaking→讲解，error→待命异常色 |
| 真实状态联动 | 通过 | 录音、TTS 实际播放、`answer.delta`、Agent/Tool 运行、TTS 加载、应急操作和 Agent 失败均按固定优先级驱动 |
| 后台轮询隔离 | 通过 | 应急五秒轮询 `polling` 未接入数字人信号，仅用户触发的 `actionBusy` 驱动思考状态 |
| 演示页 | 通过 | 六状态手动切换、开始/暂停/重新播放循环演示及本地 TTS 汇报已接入，共用生产渲染器 |
| 降级与无障碍 | 通过 | WebP→PNG→待命图→文字占位逐级回退；状态使用可访问文本；减少动态偏好下停止循环动画 |
| 前端测试 | 通过 | 17 个 Vitest 文件、71 项测试全部通过；原有 59 项测试保持通过 |
| 双入口构建 | 通过 | TypeScript 检查及主页面、`digital-human-demo.html` 生产构建成功 |
| 响应式视觉检查 | 通过 | 1440×900、1366×768、820×900、390×844 均无水平溢出，三姿态资产全部成功加载 |
| DGX 镜像构建 | 通过 | Speech 6 项测试、Frontend 71 项测试及双入口构建、Java 全 reactor 测试和打包均在 ARM64 容器构建中通过 |
| DGX 启动与页面 | 通过 | Backend、Frontend、Speech 均为 healthy；主页面与 `/digital-human-demo.html` 均返回 HTTP 200 |
| 生产资产校验 | 通过 | DGX 返回的 `guardian-idle.webp` SHA-256 为 `faf720dce8a40a49f146a016a09ca85cc8eb8e568df6b0cdb3dc7e49b5c11dae`，与资产清单一致 |
| DGX 冒烟 | 通过 | Qwen 普通、流式、3 次结构化响应通过；Speech 能力显示 ASR `small`、TTS `Serena` 均可用 |
| 模型配置保持 | 通过 | Backend 仍使用 `http://qwen:8000/v1/chat/completions`、`qwen3.6-35b-a3b-nvfp4`、无鉴权、`enable_thinking=false` |

本次只修改前端数字人渲染、状态接入、演示页和静态资产，并增强 Windows→DGX 同步脚本对历史 root 所有者临时目录的安全清理；后端 REST/SSE、Qwen、ASR、Qwen3-TTS、MySQL 及模型配置均未改变。代码已同步到 `/home/whtc/workspace/projects/road-agent-dgx`，新镜像已构建并完成启动与冒烟验收。
