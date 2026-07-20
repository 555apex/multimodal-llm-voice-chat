# 三人学习与开发任务

当前代码是一条“能运行、能测试”的基线，不是整个项目的最终实现。三人先共同阅读公共契约，再在自己的模块中练习，避免直接修改其他人的具体实现。

## 1. 共同冻结的最小契约

本轮先保持以下内容稳定：

- 前端接口：`POST /api/v1/traffic/queries`；
- 业务输入：行政区划代码、道路名称、可选方向；
- `TrafficDataPort`：取得内部标准 `TrafficSnapshot`；
- `ChatModelPort`：输入 `ModelRequest`，输出 `ModelResponse`；
- 响应必须携带来源、获取时间、Mock标识和Trace ID。

调整这些内容前，三人应先讨论，因为修改会同时影响多个模块。

## 2. Agent与业务主线（约50%）

先阅读：

- `RealtimeTrafficSkill.java`；
- `TrafficWorkflowStep.java`；
- `TrafficQuery.java`；
- `RealtimeTrafficSkillTest.java`。

建议练习：

1. 增加“道路名称过短或仅包含空白”的领域测试；
2. 给未来时间的数据增加更明确的 `UNKNOWN` 警告；
3. 改进规则摘要，使其分别统计畅通、缓行、拥堵路段数量；
4. 为Prompt增加测试，证明没有把API密钥或技术配置发给模型；
5. 设计第二个只读Skill时，复用工作流思想，但不要急于创建万能Planner。

验收：新增规则必须有单元测试；关闭模型API时交通查询仍可工作。

## 3. 数据与Tool支线（约30%）

先阅读：

- `QueryRealtimeTrafficTool.java`；
- `MockTrafficDataAdapter.java`；
- `AmapTrafficDataAdapter.java`；
- `OpenAiCompatibleChatModelAdapter.java`。

建议练习：

1. 为Mock增加 `partial` 和 `timeout` 场景；
2. 补充高德缺少道路列表、速度无法解析等测试；
3. 将高德错误码映射整理为独立类；
4. 记录外部调用耗时，但禁止记录API密钥；
5. 数据团队接口确定后新增 `ClientTrafficDataAdapter`，不得把对方DTO传入核心层。

验收：Mock和高德适配器都满足同一个 `TrafficDataPort`契约；外部错误不能伪装成正常数据。

## 4. 接口与交互支线（约20%）

先阅读：

- `TrafficController.java`；
- `TrafficQueryRequest.java`；
- `frontend/src/stores/traffic.ts`；
- 两个Vue组件。

建议练习：

1. 将后端警告码转换成用户易懂的中文；
2. 为“空数据”和“陈旧数据”分别设计视觉状态；
3. 增加最近三次查询记录，只保存在浏览器内存；
4. 为结果面板增加组件测试；
5. 后续有真实地图需求时，再讨论高德JS地图，不在当前页面直接嵌入。

验收：加载、成功、空数据、参数错误和上游错误五种状态均能明确呈现。

## 5. 集成规则

- 不在模块之间复制DTO；需要共享的内部对象放在 `domain` 或 `application`；
- 不让外部平台DTO进入 `core`；
- 不在Controller里编写业务流程；
- 不让大模型生成或改变拥堵等级；
- 每人提交前至少运行自己模块测试，集成负责人运行全部测试。
