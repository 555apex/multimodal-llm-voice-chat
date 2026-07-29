# 三人学习与开发任务

当前代码是“交通问答 + MySQL 应急调度”的可运行基线，不是最终产品。三人先理解公共契约，再在各自模块继续实现。

## 1. 共同维护的最小契约

- 对话入口、应急事件接口及 SSE 事件名称；
- `AgentDecision` 中的 `trafficScope`、行政区、交通结果和调度方案等内部对象；
- 交通和模型 Port；
- `AbnormalEventPort`、`DispatchRepository`、`UnitOfWork`；
- `ResourceDataPort`、`WorkOrderPort` 等未来扩展端口；
- 已注册的意图和 Skill 白名单；
- 事件状态、调度方案版本、审批幂等键和并发更新规则。

修改这些内容前应先讨论，因为它们会同时影响多个模块。外部厂商 DTO 和数据库实现不得进入 `core`。

## 2. Agent 与业务主线（约 50%）

先阅读：

- `AgentRuntime.java`、`IntentPlanner.java`、`SkillRegistry.java`；
- `RealtimeTrafficSkill.java`、`EmergencyDispatchSkill.java`；
- `DispatchApplicationService.java` 及对应测试。

建议练习：

1. 增加意图模型非法字段、缺失参数和修复失败测试；
2. 增加“继续问北向南”的多轮会话用例；
3. 增加 `ROAD / AREA_ALL / AREA_MAJOR` 连续追问和非福建问题测试；
4. 增加调度重复生成、版本冲突、模型失败重试和多次返工历史测试；
5. 为新增 Skill 明确输入、步骤、Tool、审批点和结果对象。

验收：模型不能绕过 Skill 白名单、事件来源和审批规则；模型或外部服务失败时不得伪造成功结果。

## 3. 数据、Tool 与知识支线（约 30%）

先阅读：

- `QueryRealtimeTrafficTool.java`、`QueryEmergencyResourcesTool.java`；
- `AmapTrafficDataAdapter.java`、`AmapAdministrativeAreaAdapter.java`、`AmapAreaTrafficDataAdapter.java`；
- `OpenAiCompatibleChatModelAdapter.java`；
- `AbnormalEventRepository.java`、`MysqlDispatchRepository.java`、`SpringUnitOfWork.java`。

建议练习：

1. 补充高德无道路、无权限、超时和异常字段测试；
2. 理解 6 公里分片、500 片保护、并发采集、短时缓存和部分覆盖的实现；
3. 给结构化模型输出增加“一次修复成功”和“两次失败”测试；
4. 补充 MySQL 条件更新、重复生成、审批事务和失效 `GENERATING` 重试测试；
5. 记录外部调用耗时，但禁止记录 API 密钥、数据库密码和完整敏感请求；
6. 建立真实救援资源库后实现 `ResourceDataPort` 适配器；
7. 外部工单平台确定后实现 `WorkOrderPort` 适配器。

验收：自动测试不访问真实高德或 DeepSeek；生产装配使用 MySQL 事件和工单仓储，不使用内存工单或 Mock 工单。资源库和外部工单平台未接入前，不伪造库存或下发结果。

## 4. 接口与交互支线（约 20%）

先阅读：

- `AgentController.java`、`EmergencyEventController.java`、`DispatchController.java`；
- `frontend/src/api/agentApi.ts`、`frontend/src/api/emergencyApi.ts`；
- `frontend/src/stores/agent.ts`、`frontend/src/stores/emergency.ts`；
- `ChatMessage.vue`、`TrafficResultPanel.vue`、`EmergencyAlertCard.vue`。

建议练习：

1. 将交通 warning code 转换为清晰中文；
2. 维护区域整体指标、覆盖率、道路搜索、状态筛选和 50 条分页；
3. 完善 SSE 断线、失败和重复点击审批的交互；
4. 覆盖应急轮询暂停/恢复、二次确认、审批、返工、失败重试和处理后切换；
5. 增加数字人“聆听、思考、回答、失败”状态测试；
6. 为小屏和大屏布局增加视觉验收；
7. 后续分别接入 ASR 和 TTS，不让语音逻辑进入 Agent 核心。

验收：失败时丢弃未完成回答；交通卡片只依据结构化事件展示；顶部告警独立于聊天运行状态，正式调度操作只能关联数据库事件。

## 5. 集成规则

- 不在 Controller 或 Vue 组件中编写业务状态规则；
- 不让大模型直接访问数据库、修改事件或审批工单；
- 不让外部平台 DTO 进入 `core`；
- 不在 Git 中保存 API 密钥和数据库密码；
- 数据库结构变更使用独立、可核验的 SQL 脚本，不在应用启动时自动执行；
- 每人提交前运行自己模块测试，集成负责人运行全部后端、前端测试和构建。
