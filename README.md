# 福建应急交通 Agent

这是一个不依赖 LangChain 的教学型 Agent 项目。当前已经打通两条纵向闭环：

```text
交通问答：自然语言 → DeepSeek识别道路/区域范围 → 交通Skill → 高德Tool → DeepSeek流式回答
应急调度：事件描述 → DeepSeek提取信息 → Mock资源 → 调度草案 → 人工审批 → Mock工单
```

DeepSeek负责理解和生成；Java负责 Skill 白名单、参数校验、Tool 调用、审批和状态转换。模型不能直接创建工单或修改业务状态。

## 1. 环境要求

- JDK 17；
- Node.js 20或更高版本；
- 无需安装全局 Maven。仓库已包含 Maven Wrapper 3.3.4，并固定 Maven 3.9.16。

## 2. 配置本地密钥

后端默认使用高德交通数据和 OpenAI 兼容模型，因此启动前必须配置密钥。

```bash
cp config/api-test.env.example config/api-test.env
```

打开本地文件 `config/api-test.env`，填入已重新生成的高德和 DeepSeek 密钥，然后执行：

```bash
source config/api-test.env
```

`config/api-test.env` 已被 Git 忽略。不要把密钥写入 Java、`application.yml` 或示例文件。

如果未来服务器模型兼容 OpenAI 协议，只需修改：

```bash
export ROADAGENT_MODEL_ENDPOINT=http://服务器地址/v1/chat/completions
export ROADAGENT_MODEL_NAME=服务器模型名
export ROADAGENT_MODEL_AUTH_ENABLED=false
```

如果服务器模型协议不同，应新增 `ChatModelPort` 适配器，Agent、Skill、Tool 和前端接口不需要修改。

## 3. 启动项目

### 3.1 后端

在项目根目录执行：

```bash
./mvnw test
./mvnw package -DskipTests
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

Windows 将 `./mvnw` 替换为 `mvnw.cmd`。首次执行会下载 Maven 和 Java 依赖到用户缓存，不会安装全局 Maven。

如果缺少密钥，后端会在启动阶段明确报错；不会自动切换为交通 Mock 或规则摘要。

### 3.2 前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 `http://localhost:8080`。

## 4. 当前能力与边界

### 4.1 交通问答

- 同一个交通Skill支持三种范围：`ROAD`具体道路、`AREA_ALL`行政区整体、`AREA_MAJOR`行政区主要道路；
- 道路查询缺少城市或道路时继续追问；区域查询不再强制追问某一条路；
- 行政区名称由高德行政区服务解析，Java校验adcode必须属于福建，不采信模型生成的编码；
- “交通要道”使用高德道路等级4查询，不让模型凭自身知识列举道路；
- 区域边界以约6公里矩形分片，最多500片、并发4个；部分失败时明确显示覆盖率；
- Java根据全部成功分片的去重路段计算畅通、缓行、拥堵比例和平均速度，模型只负责解释统计结果；
- 会话最多保留20条消息，闲置60分钟后失效；
- 高德适配器当前验证福州、厦门、泉州，其他福建城市提示数据源覆盖不足；
- 高德或 DeepSeek 失败时，本次请求失败，不返回虚构数据或规则摘要；
- 保留 `POST /api/v1/traffic/queries`，用于结构化交通查询兼容。

示例：

```text
福州五四路现在堵吗？
北向南呢？
厦门市思明区的交通情况如何？
思明区交通要道现在通行情况如何？
```

### 4.2 最小应急调度

- 应急资源和工单暂用 Mock，模型只依据已有资源生成方案；
- 方案生成后停在 `WAITING_APPROVAL`；
- 聊天中输入“确认”不会创建工单，必须点击方案卡片的批准按钮；
- 驳回后不创建工单；批准后创建唯一 Mock 工单并结束于 `SUBMITTED`；
- 重复批准使用幂等键返回同一张工单，不重复创建。

示例：

```text
福州五四路发生车辆碰撞，占用两条车道，请生成应急调度方案。
```

## 5. 工程模块

| 模块 | 作用 | 主要内容 |
|---|---|---|
| `road-agent-domain` | 纯业务对象和规则 | 福建城市、行政区边界、区域交通指标、调度状态与方案 |
| `road-agent-application` | 模块间稳定契约 | 用例、Port、命令、结果、Agent事件 |
| `road-agent-core` | Agent和业务工作流 | 意图规划、Skill注册、交通Skill、调度Skill、审批服务 |
| `road-agent-adapters` | 外部能力实现 | 高德道路/行政区/矩形交通、DeepSeek、Mock资源与工单、内存会话 |
| `road-agent-interface` | HTTP边界 | REST、SSE、请求响应DTO和错误转换 |
| `road-agent-boot` | 统一装配 | Spring Boot启动、配置和具体实现选择 |
| `frontend` | 对话界面 | 数字人、流式消息、交通卡片、调度审批卡片 |

依赖方向：

```text
interface / core / adapters → application → domain
boot → 装配全部模块
```

外部平台 DTO 不进入核心层。未来接入甲方交通、资源或工单接口时，新增对应 Port 的 Adapter 即可。

## 6. 主要接口

```text
POST /api/v1/conversations/{conversationId}/messages/stream
GET  /api/v1/dispatches/{planId}
POST /api/v1/dispatches/{planId}/approvals
POST /api/v1/traffic/queries
POST /api/v1/traffic/area-queries
```

流式入口返回 SSE 事件，包括运行阶段、意图、Skill、Tool、文字增量、业务结果、审批要求和失败信息。接口契约见 [contracts/openapi/traffic-api.yaml](contracts/openapi/traffic-api.yaml)。

## 7. 推荐阅读顺序

1. `AgentController`：自然语言请求如何进入后端；
2. `AgentRuntime`：规划、选择 Skill、执行和记忆如何串联；
3. `IntentPlanner`与`SkillRegistry`：模型选择和 Java 白名单的边界；
4. `RealtimeTrafficSkill`与`EmergencyDispatchSkill`：交通三种范围和调度业务流程；
5. `QueryRealtimeTrafficTool`、`QueryAreaTrafficTool`与各类 Port：Tool 和外部接口如何隔离；
6. `AmapAdministrativeAreaAdapter`与`AmapAreaTrafficDataAdapter`：行政区校验、分片、并发、合并与覆盖率；
7. `OpenAiCompatibleChatModelAdapter`：结构化输出和流式输出如何实现；
8. [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)：三人后续练习任务。

## 8. 验证命令

自动测试不会请求真实高德或 DeepSeek：

```bash
./mvnw test
cd frontend && npm test -- --run
cd frontend && npm run build
```

当前未实现数据库持久化、知识库、语音、地图可视化和真实工单下发。
