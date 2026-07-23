# 三人学习与开发任务

当前代码是“交通问答 + 最小应急调度”的可运行基线，不是最终产品。三人先理解公共契约，再在各自模块继续实现。

## 1. 共同维护的最小契约

- 对话入口及SSE事件名称；
- `AgentDecision`中的`trafficScope`、行政区、交通结果和调度方案等内部对象；
- `TrafficDataPort`、`AdministrativeAreaPort`、`AreaTrafficDataPort`、`ChatModelPort`、`ResourceDataPort`、`WorkOrderPort`；
- 已注册的意图和Skill白名单；
- 调度状态、方案版本和审批幂等键。

修改这些内容前应先讨论，因为它们会同时影响多个模块。外部厂商DTO不得进入`core`。

## 2. Agent与业务主线（约50%）

先阅读：

- `AgentRuntime.java`、`IntentPlanner.java`、`SkillRegistry.java`；
- `RealtimeTrafficSkill.java`、`EmergencyDispatchSkill.java`；
- `DispatchApplicationService.java`及对应测试。

建议练习：

1. 增加意图模型非法字段、缺失参数和修复失败测试；
2. 增加“继续问北向南”的多轮会话用例；
3. 增加`ROAD / AREA_ALL / AREA_MAJOR`连续追问和非福建问题测试；
4. 补充调度方案资源白名单、版本冲突和失败恢复测试；
5. 为新增Skill明确输入、步骤、Tool、审批点和结果对象。

验收：模型不能绕过Skill白名单和审批规则；模型或外部服务失败时不得伪造成功结果。

## 3. 数据、Tool与知识支线（约30%）

先阅读：

- `QueryRealtimeTrafficTool.java`、`QueryEmergencyResourcesTool.java`；
- `AmapTrafficDataAdapter.java`、`AmapAdministrativeAreaAdapter.java`、`AmapAreaTrafficDataAdapter.java`；
- `OpenAiCompatibleChatModelAdapter.java`；
- `MockResourceDataAdapter.java`、`MockWorkOrderAdapter.java`。

建议练习：

1. 补充高德无道路、无权限、超时和异常字段测试；
2. 理解6公里分片、500片保护、并发采集、短时缓存和部分覆盖的实现；
3. 给结构化模型输出增加“一次修复成功”和“两次失败”测试；
4. 扩充不同城市和事件类型的Mock资源；
5. 记录外部调用耗时，但禁止记录API密钥和完整敏感请求；
6. 甲方接口确定后新增Adapter，并转换为内部标准对象。

验收：自动测试不访问真实服务；Mock只用于暂时没有真实接口的资源和工单，不用于交通运行链路。

## 4. 接口与交互支线（约20%）

先阅读：

- `AgentController.java`、`DispatchController.java`；
- `frontend/src/api/agentApi.ts`；
- `frontend/src/stores/agent.ts`；
- `ChatMessage.vue`、`TrafficResultPanel.vue`、`DispatchPlanCard.vue`。

建议练习：

1. 将交通warning code转换为清晰中文；
2. 维护区域整体指标、覆盖率、道路搜索、状态筛选和50条分页；
3. 完善SSE断线、失败和重复点击审批的交互；
4. 增加数字人“聆听、思考、回答、失败”状态测试；
5. 为小屏和大屏布局增加视觉验收；
6. 后续再分别接入ASR和TTS，不让语音逻辑进入Agent核心。

验收：失败时丢弃未完成回答；交通卡片和调度卡片只依据结构化事件展示。

## 5. 集成规则

- 不在Controller或Vue组件中编写业务状态规则；
- 不让大模型直接访问数据库或创建工单；
- 不让外部平台DTO进入`core`；
- 不在Git中保存API密钥；
- 每人提交前运行自己模块测试，集成负责人运行全部后端、前端测试和构建。
