# 福建应急交通 Agent

这是一个不依赖 LangChain 的教学型 Agent 项目，当前已经打通两条纵向闭环：

```text
交通问答：自然语言 → DeepSeek 识别道路/区域范围 → 交通 Skill → 高德 Tool → DeepSeek 流式回答
应急调度：MySQL 异常事件 → 顶部告警卡 → 模型生成版本化工单 → 人工审批或返工 → 数据库留痕
```

DeepSeek 负责理解和生成；Java 负责 Skill 白名单、参数校验、Tool 调用、审批和状态转换。模型不能直接创建工单或修改业务状态。

## 1. 环境要求

- Git；
- JDK 17；
- Node.js 20 或更高版本；
- 能访问项目负责人共享的 MySQL 8 数据库；
- 无需安装全局 Maven。仓库已包含 Maven Wrapper 3.3.4，并固定 Maven 3.9.16。

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

- `config/api-test.env`：真实 API 密钥和 MySQL 密码；
- `.idea/`：每位协作者自己的 IDEA 配置和数据库工具连接；
- `target/`、`node_modules/`、`frontend/dist/`：可重新生成的构建产物和依赖；
- MySQL 中的真实表数据。

仓库会保留 Maven Wrapper、`package-lock.json`、配置示例和数据库变更脚本，因此协作者不需要复制项目负责人的本地工程目录。

## 3. 获取共享配置

项目负责人需要通过安全渠道向协作者提供：

- `AMAP_API_KEY`；
- `ROADAGENT_MODEL_API_KEY`；
- `ROADAGENT_DB_URL`；
- `ROADAGENT_DB_USERNAME`；
- `ROADAGENT_DB_PASSWORD`。

协作者在项目根目录执行：

```bash
cp config/api-test.env.example config/api-test.env
```

然后把收到的真实值填入本地 `config/api-test.env`，每次新开终端后执行：

```bash
source config/api-test.env
```

`config/api-test.env` 已被 Git 忽略，不能提交。不要把密钥或密码写入 Java、`application.yml`、README 或示例文件。

通过 IDEA 启动时，在 `Run → Edit Configurations → RoadAgentApplication → Environment variables` 中填写同样的环境变量。IDEA 右侧数据库工具中的数据源只供查看和执行 SQL，不会自动成为 Spring Boot 的运行时数据源。

如果使用兼容 OpenAI 协议的其他模型，可额外修改：

```bash
export ROADAGENT_MODEL_ENDPOINT=http://服务器地址/v1/chat/completions
export ROADAGENT_MODEL_NAME=服务器模型名
export ROADAGENT_MODEL_AUTH_ENABLED=false
```

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
- `del_flag=0` 或 `NULL`：有效数据，`del_flag=1`：逻辑删除。

`w_abnormal_event.id` 是 `BIGINT`，后端会以字符串形式返回给前端，避免 JavaScript 精度丢失。若需要在 IDEA 中查看共享数据库，应单独创建 MySQL 数据源并选择连接参数中的 schema；刷新表列表只会刷新 IDEA 缓存，不会改变后端连接。

## 5. 启动项目

### 5.1 后端

首次拉取项目或 `pom.xml` 发生变化后，在 IDEA 中执行一次 Maven Reload；普通启动不需要每次都刷新 Maven。

在已经执行 `source config/api-test.env` 的终端中运行：

```bash
./mvnw package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

Windows 使用 `mvnw.cmd`，并在 PowerShell 或 IDEA 运行配置中设置环境变量。首次执行会下载 Maven 和 Java 依赖到用户缓存，不会安装全局 Maven。

后端启动成功后可检查：

```bash
curl http://localhost:8080/api/v1/emergency-events/pending/next
```

如果共享数据库中存在 `event_status=0` 且未逻辑删除的事件，接口会返回最早的一条事件、已有最新工单以及待处理总数。

### 5.2 前端

另开一个终端：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 `http://localhost:8080`，因此必须先保证后端已启动。

页面可见时每 5 秒查询一次待处理事件。待处理事件会显示在聊天区顶部红色告警卡中，但不会阻塞用户继续进行交通问答。

## 6. 当前能力与边界

### 6.1 交通问答

- 同一个交通 Skill 支持三种范围：`ROAD` 具体道路、`AREA_ALL` 行政区整体、`AREA_MAJOR` 行政区主要道路；
- 道路查询缺少城市或道路时继续追问；区域查询不再强制追问某一条路；
- 行政区名称由高德行政区服务解析，Java 校验 adcode 必须属于福建，不采信模型生成的编码；
- “交通要道”使用高德道路等级 4 查询，不让模型凭自身知识列举道路；
- 区域边界以约 6 公里矩形分片，最多 500 片、并发 4 个；部分失败时明确显示覆盖率；
- Java 根据全部成功分片的去重路段计算畅通、缓行、拥堵比例和平均速度，模型只负责解释统计结果；
- 会话最多保留 20 条消息，闲置 60 分钟后失效；
- 高德适配器当前验证福州、厦门、泉州，其他福建城市提示数据源覆盖不足；
- 高德或 DeepSeek 失败时，本次请求失败，不返回虚构数据或规则摘要；
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

## 7. 工程模块

| 模块 | 作用 | 主要内容 |
|---|---|---|
| `road-agent-domain` | 纯业务对象和规则 | 福建城市、行政区边界、区域交通指标、事件和版本化调度方案 |
| `road-agent-application` | 模块间稳定契约 | UseCase、Port、命令、结果、Agent 事件 |
| `road-agent-core` | Agent 和业务工作流 | 意图规划、Skill 注册、交通 Skill、调度生成与审批编排 |
| `road-agent-adapters` | 外部能力实现 | 高德、DeepSeek、MySQL 事件与工单仓储、事务适配器、内存会话 |
| `road-agent-interface` | HTTP 边界 | REST、SSE、请求响应 DTO 和错误转换 |
| `road-agent-boot` | 统一装配 | Spring Boot 启动、配置和具体实现选择 |
| `frontend` | 对话界面 | 数字人、流式消息、交通卡片、独立应急告警状态和审批交互 |

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
```

流式入口返回 SSE 事件，包括运行阶段、意图、Skill、Tool、文字增量、业务结果、审批要求和失败信息。接口契约见 [contracts/openapi/traffic-api.yaml](contracts/openapi/traffic-api.yaml)。

## 9. 推荐阅读顺序

1. `AgentController`：自然语言请求如何进入后端；
2. `AgentRuntime`：规划、选择 Skill、执行和记忆如何串联；
3. `IntentPlanner` 与 `SkillRegistry`：模型选择和 Java 白名单的边界；
4. `RealtimeTrafficSkill`：交通三种范围如何执行；
5. `EmergencyEventController` 与 `DispatchApplicationService`：数据库事件、生成、审批、返工和无需调度流程；
6. `QueryRealtimeTrafficTool`、`QueryAreaTrafficTool` 与各类 Port：Tool 和外部接口如何隔离；
7. `AbnormalEventRepository`、`MysqlDispatchRepository` 与 `SpringUnitOfWork`：MySQL 持久化和事务边界；
8. `OpenAiCompatibleChatModelAdapter`：结构化输出和流式输出如何实现；
9. [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)：三人后续练习任务。

## 10. 验证命令

自动测试不会请求真实高德或 DeepSeek。设置共享数据库环境变量后，后端集成测试会验证真实 MySQL 仓储：

```bash
./mvnw test
cd frontend && npm test -- --run
cd frontend && npm run build
```

## 11. 常见问题

### IDEA 右侧看不到新表

确认 IDEA 数据源连接的是 `ROADAGENT_DB_URL` 中的同一台 MySQL 和同一个 schema，然后对该 schema 执行 `Synchronize` 或刷新。`.idea/` 不会上传，所以协作者需要各自新建数据源。

### 有待处理事件但前端没有红色告警

依次确认：

1. 后端已在 `8080` 端口启动；
2. `GET /api/v1/emergency-events/pending/next` 能返回事件；
3. 事件满足 `event_status=0 AND COALESCE(del_flag, 0)=0`；
4. 前端已在 `5173` 端口启动，浏览器页面处于可见状态；
5. 前后端终端中没有数据库连接或代理错误。

### 是否每次都要 Maven Reload 或运行测试

不需要。首次拉取或 `pom.xml` 变化后执行 Maven Reload；提交代码前运行测试。平时启动只需加载环境变量后运行后端和前端。

### 当前尚未实现的部分

统一救援资源数据库、知识库、语音、地图可视化和外部工单平台尚未接入。MySQL 异常事件读取、工单持久化、审批、返工和无需调度留痕已经实现。
