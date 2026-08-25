# 福建应急交通 Agent

这是一个不依赖 LangChain 的教学型 Agent 项目，当前已经打通三条纵向闭环：

```text
交通问答：自然语言 → OpenAI-compatible 模型识别道路/区域范围 → 交通 Skill → 高德 Tool → Java确定性回答
应急调度：MySQL 异常事件 → 顶部告警卡 → 模型生成版本化工单 → 人工审批或返工 → 数据库留痕
语音交互：浏览器录音 → Java语音接口 → Docker内faster-whisper识别；回答摘要 → 本地Qwen3-TTS分段合成 → 浏览器播放
```

OpenAI-compatible 模型负责意图理解和应急方案生成；DGX 部署使用本地 Qwen3.6-35B。Java 负责交通事实回答、Skill 白名单、参数校验、Tool 调用、审批和状态转换。模型不能直接创建工单、修改业务状态或改写交通事实。

## 1. 环境要求

- Git；
- JDK 17；
- Node.js 20 或更高版本；
- Docker Desktop，或 Docker Engine + Docker Compose v2，仅语音功能需要；
- 能访问项目负责人共享的 MySQL 8 数据库；
- 无需安装全局 Maven。仓库已包含 Maven Wrapper 3.3.4，并固定 Maven 3.9.16。

语音服务的 Python 3.12、faster-whisper、Qwen3-TTS 和 FFmpeg 全部安装在 Docker 镜像中。本机不需要安装 Python 或 Conda；不启动语音容器时，文字问答、交通查询和应急调度仍可正常使用。

ASR 固定为 `small + CPU + int8`；TTS 使用 Qwen3-TTS 0.6B、Serena 中文女声和 NVIDIA GPU。DGX 专用容器基于 NVIDIA PyTorch ARM64 镜像，两个模型均需先下载到只读模型目录。

DGX 运行镜像包含 NVIDIA PyTorch、Qwen3-TTS 和音频依赖，镜像及模型占用明显高于旧版 CPU-only 语音容器。部署前应至少预留 20 GB 磁盘，并按 `deploy/dgx/README.md` 固定镜像和模型 revision。

## 2. 拉取指定版本

```bash
git clone --branch version/roadagent-v1 --single-branch \
  https://github.com/555apex/multimodal-llm-voice-chat.git
cd multimodal-llm-voice-chat
```

如果已经克隆过仓库：

```bash
git fetch origin
git switch version/roadagent-v1
git pull --ff-only origin version/roadagent-v1
```

以下内容不会上传到 GitHub：

- 根目录 `.env`：协作者自己的 Docker 语音容器参数；
- `config/api-test.env`、`config/api-test.ps1`：真实 API 密钥和 MySQL 密码；
- `.idea/`：每位协作者自己的 IDEA 配置和数据库工具连接；
- `target/`、`node_modules/`、`frontend/dist/`：可重新生成的构建产物和依赖；
- Docker 命名卷中的 faster-whisper 模型，以及请求期间使用的临时音频；
- MySQL 中的真实表数据。

仓库会保留 Maven Wrapper、`package-lock.json`、配置示例和数据库变更脚本，因此协作者不需要复制项目负责人的本地工程目录。

## 3. 准备 API 和共享数据库配置

运行项目需要两类外部 API 和一个共享云数据库。API Key 由每位协作者使用自己的账号申请，数据库连接信息由项目负责人通过安全渠道提供。

| 配置 | 由谁准备 | 要求 |
|---|---|---|
| `AMAP_API_KEY` | 协作者 | 在[高德开放平台](https://lbs.amap.com/api/webservice/create-project-and-key)创建应用并申请“Web 服务”类型的 Key；确认账号具有项目所用交通态势和行政区接口的调用额度 |
| `ROADAGENT_MODEL_API_KEY` | 协作者 | 在[DeepSeek 开放平台](https://platform.deepseek.com/api_keys)创建 API Key，并确认账号有可用余额和调用额度 |
| `ROADAGENT_DB_URL` | 项目负责人 | 完整 JDBC URL，包含数据库 IP/域名、端口、schema 和连接参数 |
| `ROADAGENT_DB_USERNAME` | 项目负责人 | 共享数据库账号 |
| `ROADAGENT_DB_PASSWORD` | 项目负责人 | 共享数据库密码 |

负责人还需要告知协作者数据库是否要求校园网、VPN 或 IP 白名单，并确认第 4 节所列的表结构和读写权限已经准备好。真实 IP、账号、密码和任何 API Key 都不能写入 README、示例文件或 Java 配置。

### 3.1 macOS / Linux

在项目根目录执行：

```bash
cp config/api-test.env.example config/api-test.env
```

然后把自己申请的两个 API Key 和负责人提供的数据库信息填入本地 `config/api-test.env`，每次新开终端后执行：

```bash
source config/api-test.env
```

`config/api-test.env` 已被 Git 忽略，不能提交。

### 3.2 Windows PowerShell

在项目根目录执行：

```powershell
Copy-Item config/api-test.ps1.example config/api-test.ps1
```

填写自己申请的 API Key 和负责人提供的数据库信息后，每次新开 PowerShell 执行：

```powershell
. .\config\api-test.ps1
```

`config/api-test.ps1` 同样已被 Git 忽略，不能提交。PowerShell 配置只对当前终端会话生效。

### 3.3 IDEA

通过 IDEA 启动时，在 `Run → Edit Configurations → RoadAgentApplication → Environment variables` 中填写同样的环境变量。IDEA 右侧数据库工具中的数据源只供查看和执行 SQL，不会自动成为 Spring Boot 的运行时数据源。

如果使用兼容 OpenAI 协议的其他模型，可额外修改：

```bash
export ROADAGENT_MODEL_ENDPOINT=http://服务器地址/v1/chat/completions
export ROADAGENT_MODEL_NAME=服务器模型名
export ROADAGENT_MODEL_AUTH_ENABLED=false
```

Java 默认通过 `http://localhost:8091` 访问语音容器。如需显式配置或临时关闭语音入口，可设置：

```bash
export ROADAGENT_SPEECH_ENABLED=true
export ROADAGENT_SPEECH_SERVICE_URL=http://localhost:8091
```

### 3.4 Docker 语音配置

Java 配置和 Docker 配置是两套独立配置，不能互相替代：

| 本地文件 | 谁读取 | 主要内容 |
|---|---|---|
| `config/api-test.env` / `config/api-test.ps1` | 当前终端中的 Java 后端 | MySQL、高德、OpenAI-compatible 模型，以及 `ROADAGENT_SPEECH_*` |
| 根目录 `.env` | `docker compose` | 本地 ASR/TTS 模型路径、计算设备和音色等 `SPEECH_*` |

Docker 语音服务已有可直接运行的默认值。需要查看或修改时，在项目根目录执行：

```bash
cp .env.example .env
```

Windows PowerShell 使用：

```powershell
Copy-Item .env.example .env
```

`.env` 不需要 `source`，Docker Compose 会自动读取；Java 不会读取它。`.env` 已被 Git 忽略，不能提交。使用第三方模型镜像前，应自行确认镜像来源和网络策略。

如果模型协议不同，应新增 `ChatModelPort` 适配器，Agent、Skill、Tool 和前端接口不需要修改。

## 4. 共享数据库要求

本版本不会在应用启动时自动建表或修改表结构。“建表脚本”是由数据库负责人执行一次的 SQL，用于创建工单表，并给事件表补充本工作流需要的字段、约束和索引。项目负责人应先确保共享数据库已经执行过：

```text
docs/sql/20260728_emergency_dispatch.sql
```

协作者连接共享数据库时不需要重复执行此脚本，也不需要 `CREATE`、`ALTER` 或 `DROP` 权限。正常运行至少需要：

- 连接共享数据库的权限；
- 对 `w_abnormal_event` 的 `SELECT`、`UPDATE` 权限；
- 对 `w_emergency_dispatch_order` 的 `SELECT`、`INSERT`、`UPDATE` 权限。

如果协作者完全没有数据库连接或上述读写权限，后端将无法查询告警或保存工单，项目的数据库应急调度功能不能运行。当前工作流使用：

- `w_abnormal_event`：保存异常事件；
- `w_emergency_dispatch_order`：按版本保存模型生成的应急调度工单。

关键状态约定：

- `event_status=0`：待处理；
- `event_status=1`：调度工单已审批通过；
- `event_status=2`：已确认无需调度；
- `del_flag=N`、`0` 或 `NULL`：有效数据；`del_flag=Y` 或 `1`：逻辑删除。

`w_abnormal_event.id` 是 `BIGINT`，后端会以字符串形式返回给前端，避免 JavaScript 精度丢失。若需要在 IDEA 中查看共享数据库，应单独创建 MySQL 数据源并选择连接参数中的 schema；刷新表列表只会刷新 IDEA 缓存，不会改变后端连接。

## 5. 启动项目

完整语音版由三个本地进程组成，启动顺序建议保持如下：

```text
Docker语音服务 :8091 → Java后端 :8080 → Vite前端 :5173 → 浏览器麦克风/扬声器
```

只使用文字功能时，可以跳过语音容器，直接启动后端和前端。

### 5.1 语音容器

先启动 Docker Desktop 或 Docker Engine。确认下面两条命令都成功后，再在项目根目录启动语音服务：

```bash
docker version
docker compose version
```

```bash
docker compose -f compose.speech.yml up --build -d
docker compose -f compose.speech.yml ps
docker compose -f compose.speech.yml logs -f speech-service
```

`logs -f` 会持续显示日志，按 `Ctrl+C` 只退出日志查看，不会停止容器。ASR/TTS 权重必须先下载到 `.env` 的 `SPEECH_MODEL_ROOT`；容器只读加载模型，不会在运行时联网下载。加载期间容器会显示为 `starting` 或未就绪。可另开终端分别检查存活和就绪状态：

```bash
curl http://localhost:8091/health/live
curl http://localhost:8091/health/ready
```

`/health/live` 返回 `status: UP` 表示进程已启动；`/health/ready` 返回 `status: UP`、`asrAvailable: true` 和 `ttsAvailable: true` 后，页面语音功能才可用。

模型保存在宿主机中央模型目录并只读挂载。停止并删除容器不会删除模型：

```bash
docker compose -f compose.speech.yml down
```

普通 `down` 后，下次启动会复用同一模型目录。Java 后端不会因语音容器离线而启动失败。

### 5.2 后端

仓库中的 Maven Wrapper 已固定使用 Maven 3.9.16：macOS/Linux 使用 `mvnw`，Windows 使用 `mvnw.cmd`，版本和下载地址保存在 `.mvn/wrapper/maven-wrapper.properties`。当前 Wrapper 采用官方 `distributionType=only-script`，因此不需要也不会包含 `maven-wrapper.jar`；这不是文件缺失。首次运行时会从 Maven Central 下载 Maven 和项目依赖，协作者需要能够访问 `repo.maven.apache.org`。

首次拉取项目或 `pom.xml` 发生变化后，在 IDEA 中执行一次 Maven Reload；普通启动不需要每次都刷新 Maven。

在已经执行 `source config/api-test.env` 的终端中运行：

```bash
./mvnw package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

Windows PowerShell 使用：

```powershell
.\mvnw.cmd package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

首次执行会下载 Maven 和 Java 依赖到用户缓存，不会安装全局 Maven。

后端启动成功后可检查：

```bash
curl http://localhost:8080/api/v1/emergency-events/pending/next
curl http://localhost:8080/api/v1/speech/capabilities
```

如果共享数据库中存在 `event_status=0` 且未逻辑删除的事件，接口会返回最早的一条事件、已有最新工单以及待处理总数。

### 5.3 前端

另开一个终端：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 `http://localhost:8080`，因此必须先保证后端已启动。

页面可见时每 5 秒查询一次待处理事件。待处理事件会显示在聊天区顶部红色告警卡中，但不会阻塞用户继续进行交通问答。

首次点击麦克风时，浏览器会请求录音权限，请选择允许。本机使用 `http://localhost:5173` 即可；通过其他域名或 IP 访问时必须配置 HTTPS，否则浏览器通常不会开放麦克风。

### 5.4 跑通后的语音验收

按顺序检查以下行为：

1. `GET http://localhost:8091/health/ready` 返回 ASR、TTS 均可用；
2. `GET http://localhost:8080/api/v1/speech/capabilities` 返回同样的能力状态，证明 Java 已连通容器；
3. 点击麦克风，说“福州五四路现在拥堵吗”，再次点击停止；识别结果应插入输入框当前光标处，但不会自动发送；
4. 手动发送问题，等待回答完成；点击单条回答的“朗读”，检查播放、暂停、继续和重播；
5. 打开“语音回答”后再提一个问题，只应自动朗读之后完成的新回答；
6. 停止语音容器并刷新页面，语音按钮应显示不可用，但文字问答和应急调度仍能继续。

## 6. 当前能力与边界

### 6.1 交通问答

- 同一个交通 Skill 支持三种范围：`ROAD` 具体道路、`AREA_ALL` 行政区整体、`AREA_MAJOR` 行政区主要道路；
- 道路查询缺少城市或道路时继续追问；区域查询不再强制追问某一条路；
- 行政区名称由高德行政区服务解析，Java 校验 adcode 必须属于福建，不采信模型生成的编码；
- “交通要道”使用高德道路等级 4 查询，不让模型凭自身知识列举道路；
- 区域边界以约 6 公里矩形分片，最多 500 片、并发 4 个；部分失败时由Java自动收紧回答范围，不对外输出切片覆盖率；
- Java只依据高德返回的道路、方向、状态和速度生成确定性回答：先总结整体和重点道路，再逐条列出本次返回的全部路段；同一道路名称和方向只保留拥堵程度最高、同等级速度最低的一条；不允许模型补充路段或改写交通事实；
- 会话最多保留 20 条消息，闲置 60 分钟后失效；
- 高德适配器当前验证福州、厦门、泉州，其他福建城市提示数据源覆盖不足；
- 高德或意图识别模型失败时，本次请求失败；已取得的交通事实不交给模型自由改写；
- 保留 `POST /api/v1/traffic/queries`，用于结构化交通查询兼容。

### 6.2 数据库应急调度

- 页面可见时每 5 秒查询 `w_abnormal_event`，按发生时间最早优先展示待处理事件；
- 聊天区顶部红色告警卡独立于聊天运行状态，用户可暂时继续其他交通查询；
- 用户确认后由模型根据事件事实和通用应急知识生成资源建议清单及救援方案；
- 每次生成和返工都保存到 `w_emergency_dispatch_order`，刷新页面或重启服务后可恢复；
- 方案停在 `WAITING_APPROVAL`，批准后事件状态改为 `1`；
- 驳回必须填写意见，旧版本保留为 `REJECTED`，模型生成下一版本；
- 选择“不生成”必须填写原因并二次确认，事件状态改为 `2`；
- 模型失败会保存 `FAILED` 记录，事件保持待处理并允许重试；
- 当前资源清单是模型基于通用知识生成的建议，不代表实际库存、距离、联系人或到达时间；
- 聊天识别到应急调度意图时只引导用户使用顶部告警卡，不创建无数据库来源的正式工单。

### 6.3 语音输入与回答朗读

- 输入框麦克风按钮支持开始、停止和取消录音；最长 60 秒、最大 10 MB，识别文字插入当前光标位置，不会自动发送；
- 浏览器优先使用 WebM/Opus，按能力回退到 MP4/AAC 或 Ogg/Opus；非 `localhost` 部署需要 HTTPS 才能稳定获取麦克风权限；
- “语音回答”每次打开页面默认关闭；打开后只自动朗读之后完成的助手回答，关闭会立即停止并清空播放队列；
- 每条已完成的助手消息均有独立的播放、暂停、继续和重播按钮，同一时刻只播放一条；
- 中文按自然标点分段，播放当前段时预合成下一段，以降低首段等待和段间停顿；
- 交通查询朗读 Java 根据结构化事实生成的简短结论、最多 3 条重点道路和出行建议，不逐行朗读表格或坐标；省略明细时会提示查看页面；
- 应急告警卡和正式调度工单不会自动朗读；Qwen3-TTS 在 DGX 本地离线合成，不会把待朗读文本发送到外部服务；
- 语音容器不可用、ASR/TTS 失败或被用户取消时，只影响语音功能，不影响已有文字和其他业务流程。

## 7. 工程模块

| 模块 | 作用 | 主要内容 |
|---|---|---|
| `road-agent-domain` | 纯业务对象和规则 | 福建城市、行政区边界、区域交通指标、事件和版本化调度方案 |
| `road-agent-application` | 模块间稳定契约 | UseCase、Port、命令、结果、Agent 事件 |
| `road-agent-core` | Agent 和业务工作流 | 意图规划、Skill 注册、交通 Skill、调度生成与审批编排 |
| `road-agent-adapters` | 外部能力实现 | 高德、OpenAI-compatible 模型、MySQL 事件与工单仓储、事务适配器、内存会话 |
| `road-agent-interface` | HTTP 边界 | REST、SSE、请求响应 DTO 和错误转换 |
| `road-agent-boot` | 统一装配 | Spring Boot 启动、配置和具体实现选择 |
| `frontend` | 对话界面 | 数字人、流式消息、交通卡片、独立应急告警状态和审批交互 |
| `speech-service` | Docker语音服务 | FastAPI、faster-whisper、Qwen3-TTS、MP3转码、健康检查和离线替身测试 |

依赖方向：

```text
interface / core / adapters → application → domain
boot → 装配全部模块
```

外部平台 DTO 不进入核心层。未来接入甲方交通、资源或工单接口时，新增对应 Port 的 Adapter 即可。

## 8. 主要接口

```text
POST /api/v1/conversations/{conversationId}/messages/stream
GET  /api/v1/emergency-events/pending/next
POST /api/v1/emergency-events/{eventId}/dispatches
POST /api/v1/emergency-events/{eventId}/no-dispatch
GET  /api/v1/dispatches/{planId}
POST /api/v1/dispatches/{planId}/approvals
POST /api/v1/traffic/queries
POST /api/v1/traffic/area-queries
GET  /api/v1/speech/capabilities
POST /api/v1/speech/transcriptions
POST /api/v1/speech/syntheses
```

流式入口返回 SSE 事件，包括运行阶段、意图、Skill、Tool、文字增量、独立朗读文本 `answer.speech`、业务结果、审批要求和失败信息。接口契约见 [contracts/openapi/traffic-api.yaml](contracts/openapi/traffic-api.yaml)。

## 9. 推荐阅读顺序

1. `AgentController`：自然语言请求如何进入后端；
2. `AgentRuntime`：规划、选择 Skill、执行和记忆如何串联；
3. `IntentPlanner` 与 `SkillRegistry`：模型选择和 Java 白名单的边界；
4. `RealtimeTrafficSkill`：交通三种范围如何执行；
5. `EmergencyEventController` 与 `DispatchApplicationService`：数据库事件、生成、审批、返工和无需调度流程；
6. `QueryRealtimeTrafficTool`、`QueryAreaTrafficTool` 与各类 Port：Tool 和外部接口如何隔离；
7. `AbnormalEventRepository`、`MysqlDispatchRepository` 与 `SpringUnitOfWork`：MySQL 持久化和事务边界；
8. `OpenAiCompatibleChatModelAdapter`：结构化输出和流式输出如何实现；
9. `SpeechController`、`SpeechApplicationService` 与 `PythonSpeechServiceAdapter`：Java 如何隔离语音容器故障；
10. `speech-service/app` 与前端 `speech` Store：识别、自然分段、预合成和播放取消；
11. [语音录音停止与识别问题修复日志](docs/VOICE_INPUT_RECORDING_BUGFIX_20260812.md)：录音按钮状态回路、排查证据、修复方案与回归验证；
12. [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)：三人后续练习任务。

## 10. 验证命令

自动测试不会请求真实高德或外部模型。设置共享数据库环境变量后，后端集成测试会验证真实 MySQL 仓储：

```bash
./mvnw test
cd frontend && npm test -- --run
cd frontend && npm run build
docker compose -f compose.speech.yml --profile test run --rm speech-tests
```

Python 测试使用替身 ASR/TTS，不下载模型，也不会访问外网。Docker 运行时人工验收还应检查 ARM64 构建、固定模型加载、福建道路名称识别、Serena 中文 MP3，以及交通表格不逐行朗读。

## 11. 常见问题

### IDEA 右侧看不到新表

确认 IDEA 数据源连接的是 `ROADAGENT_DB_URL` 中的同一台 MySQL 和同一个 schema，然后对该 schema 执行 `Synchronize` 或刷新。`.idea/` 不会上传，所以协作者需要各自新建数据源。

### 有待处理事件但前端没有红色告警

依次确认：

1. 后端已在 `8080` 端口启动；
2. `GET /api/v1/emergency-events/pending/next` 能返回事件；
3. 事件满足 `event_status=0 AND (del_flag IS NULL OR del_flag IN ('N', '0'))`；
4. 前端已在 `5173` 端口启动，浏览器页面处于可见状态；
5. 前后端终端中没有数据库连接或代理错误。

### 是否每次都要 Maven Reload 或运行测试

不需要。首次拉取或 `pom.xml` 变化后执行 Maven Reload；提交代码前运行测试。平时启动只需加载环境变量后运行后端和前端。

### 语音按钮不可用或容器一直未就绪

先执行 `docker version`。如果看不到 Server 信息，说明 Docker 引擎尚未启动；先启动 Docker Engine。然后执行 `docker compose -f compose.speech.yml ps` 和 `docker compose -f compose.speech.yml logs -f speech-service`。首次启动通常是在载入 Qwen3-TTS、编译 GPU 内核或载入 ASR；检查固定模型目录、容器 GPU 和内存。`GET /api/v1/speech/capabilities` 会反映 Java 当前探测到的 ASR/TTS 状态，容器恢复后刷新页面即可。

如果日志提示 `8091` 端口已被占用，先停止占用该端口的旧进程或旧容器，再重新启动。本项目把语音端口绑定到 `127.0.0.1`，不要为了协作调试直接改成 `0.0.0.0` 暴露到局域网。

如果日志提示模型目录不存在或 manifest 校验失败，在 DGX 执行 `deploy/dgx/dgx-stack download-models`，完成后再启动服务。模型下载与运行分离，Speech 容器本身没有外网访问能力。

如果只有 TTS 失败，检查 Qwen3-TTS 模型目录、GPU、BF16、SDPA 和 FFmpeg 日志；Speech 容器运行期不需要互联网。麦克风无权限时，检查浏览器站点权限；远程部署需使用 HTTPS，本机 `http://localhost` 可直接调试。

### 当前尚未实现的部分

统一救援资源数据库、知识库、地图可视化和外部工单平台尚未接入。MySQL 应急调度、Docker ASR、分段 TTS 及前端语音交互已经实现。
