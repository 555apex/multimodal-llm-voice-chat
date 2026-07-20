# 福建应急交通 Agent：交通查询工作流

这是一个面向三人协作和教学的第一期项目骨架。当前只实现一条完整纵向闭环：

```text
Vue页面 → REST接口 → RealtimeTrafficSkill → 交通查询Tool
       → Mock/高德交通数据 → 规则/DeepSeek摘要 → 页面结果
```

项目不使用LangChain，也不要求电脑全局安装Maven。Agent的业务步骤由Java固定控制，大模型只负责整理摘要。

## 1. 环境要求

- JDK 17；
- Node.js 20或更高版本；
- 无需安装Maven，仓库已经包含Maven Wrapper 3.3.4，并固定Maven 3.9.16。

## 2. 第一次运行

### 2.1 验证并打包后端

在项目根目录执行：

```bash
./mvnw test
./mvnw package -DskipTests
```

Windows使用：

```bat
mvnw.cmd test
mvnw.cmd package -DskipTests
```

首次执行会把Maven和Java依赖下载到用户缓存，这是正常现象，不会安装全局Maven。

### 2.2 启动后端

```bash
java -jar road-agent-boot/target/road-agent-boot-0.1.0-SNAPSHOT.jar
```

默认配置是 `Mock交通数据 + Java规则摘要`，不需要任何API密钥。

### 2.3 启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 `http://localhost:8080`。

## 3. 模块与开发者边界

| 模块 | 作用 | 主要开发角色 | 当前示例 |
|---|---|---|---|
| `road-agent-domain` | 交通领域对象和不依赖框架的业务规则 | Agent与业务主线 | 查询条件、拥堵等级、路况快照 |
| `road-agent-application` | 用例、Port和模块间公共输入输出 | Agent与业务主线共同维护 | `TrafficDataPort`、`ChatModelPort`、查询用例 |
| `road-agent-core` | 固定Skill、工作流步骤、时效和降级控制 | Agent与业务主线 | `RealtimeTrafficSkill` |
| `road-agent-adapters` | Tool以及Mock、高德、模型HTTP适配 | 数据与知识支线 | 三类适配器和映射测试 |
| `road-agent-interface` | REST接口、前后端DTO、错误转换 | 接口与交互支线 | 交通查询Controller |
| `road-agent-boot` | 选择并装配各模块的具体实现 | Agent主线负责集成 | Spring Boot启动和环境变量 |
| `frontend` | 用户表单、状态管理和结果呈现 | 接口与交互支线 | Vue交通查询页 |

依赖方向：

```text
interface/core/adapters → application → domain
boot → 装配全部模块
```

`domain`不知道Spring、高德、DeepSeek和数据库；未来更换外部服务时不需要修改业务规则。

## 4. API切换

### 4.1 Mock场景

默认：

```bash
export ROADAGENT_TRAFFIC_PROVIDER=mock
export ROADAGENT_MOCK_SCENARIO=normal
```

可选场景：`normal`、`empty`、`stale`、`server_error`。修改后重启后端。

### 4.2 DeepSeek摘要

```bash
export ROADAGENT_MODEL_PROVIDER=openai-compatible
export ROADAGENT_MODEL_ENDPOINT=https://api.deepseek.com/chat/completions
export ROADAGENT_MODEL_API_KEY=替换成自己的DeepSeek密钥
export ROADAGENT_MODEL_NAME=deepseek-v4-flash
```

DeepSeek调用失败时，系统仍返回交通数据，并用Java规则生成摘要；响应中的 `summarySource` 会变为 `RULE_FALLBACK`。

### 4.3 高德交通数据

```bash
export ROADAGENT_TRAFFIC_PROVIDER=amap
export AMAP_API_KEY=替换成自己的高德Web服务Key
```

高德交通态势查询属于高级服务。普通Web Key可能返回无权限错误；系统会返回 `AMAP_AUTH_OR_PERMISSION_ERROR`，不会悄悄替换成Mock数据。

可以同时启用高德数据和DeepSeek摘要。示例环境变量见 [config/api-test.env.example](config/api-test.env.example)。

> 不要把真实密钥写入 `application.yml`、示例文件或Git。使用IDEA时，在运行配置的 Environment variables 中设置。

## 5. 手动调用接口

```bash
curl -X POST http://localhost:8080/api/v1/traffic/queries \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: learning-request-001' \
  -d '{"areaCode":"350100","roadName":"五四路","direction":"南向北"}'
```

接口契约位于 [contracts/openapi/traffic-api.yaml](contracts/openapi/traffic-api.yaml)。

## 6. 推荐阅读顺序

1. 从 `TrafficController`看前端请求如何进入Java；
2. 看 `RealtimeTrafficSkill`理解固定工作流；
3. 看 `QueryRealtimeTrafficTool`理解Tool和数据Port的区别；
4. 对比 `MockTrafficDataAdapter`和`AmapTrafficDataAdapter`；
5. 看 `OpenAiCompatibleChatModelAdapter`理解不依赖LangChain的模型HTTP调用；
6. 阅读 [docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)并领取练习任务。

## 7. 常用验证命令

```bash
./mvnw test
cd frontend && npm test
cd frontend && npm run build
```

当前阶段不包含数据库、知识库、SSE、语音、地图可视化和灾中调度工作流。
