# 开发进度表示例

建议团队在例会中维护此表。一个任务只对应一个可验收结果，不要把“完成整个Agent”写成一行。

| 编号 | 开发角色 | 业务功能 | 模块/实现类 | 输入 | 输出 | 当前状态 | 完成标准 | 测试 | 阻塞项 |
|---|---|---|---|---|---|---|---|---|---|
| AG-A01 | Agent与业务主线 | 意图识别与参数提取 | `IntentPlanner.java` | 用户消息、会话历史 | `AgentDecision` | 基线已完成 | 仅允许三种意图；非法JSON修复一次 | `AgentRuntimeTest`、模型适配器测试 | 需持续优化提示词 |
| AG-A02 | Agent与业务主线 | Skill白名单与运行时 | `AgentRuntime.java`、`SkillRegistry.java` | `AgentDecision` | SSE阶段与业务结果 | 基线已完成 | 未注册能力不可执行；失败发`run.failed` | `AgentRuntimeTest` | 无 |
| TQ-A01 | Agent与业务主线 | 道路与区域交通Skill | `IntentPlanner.java`、`RealtimeTrafficSkill.java` | 城市、行政区、道路、范围 | 路况回答与统一交通结果 | 基线已完成 | 正确选择`ROAD / AREA_ALL / AREA_MAJOR`；模型不编造交通要道 | `IntentPlannerTest`、`RealtimeTrafficSkillTest` | 高德高级服务权限 |
| ED-A01 | Agent与业务主线 | 数据库调度生成与审批规则 | `DispatchApplicationService.java` | 数据库事件、审批决定、驳回意见 | 版本化调度方案与事件状态 | 已完成 | 未批准不结束事件；重复生成和审批幂等；驳回保留历史并生成下一版 | 调度核心服务测试 | 外部工单平台未提供 |
| TQ-B01 | 数据与Tool支线 | 高德交通Tool与映射 | `QueryRealtimeTrafficTool.java`、`AmapTrafficDataAdapter.java` | `TrafficQuery` | `TrafficSnapshot` | 基线已完成 | 覆盖、状态、速度和错误正确映射 | `AmapTrafficDataAdapterTest` | 目前仅验证三座城市 |
| TQ-B02 | 数据与Tool支线 | 行政区解析与区域分片 | `QueryAreaTrafficTool.java`、`AmapAdministrativeAreaAdapter.java`、`AmapAreaTrafficDataAdapter.java` | 行政区名称、查询范围 | 去重路段、指标、覆盖率 | 基线已完成 | 6公里分片、不超过500片、部分失败可见 | 行政区与区域Adapter测试 | 目前仅高德覆盖三座城市 |
| ED-B01 | 数据与Tool支线 | MySQL事件与版本化工单 | `AbnormalEventRepository.java`、`MysqlDispatchRepository.java`、`SpringUnitOfWork.java` | 待处理事件、模型生成结果 | 最新工单、历史版本、事件状态 | 已完成 | 条件更新防并发；审批跨表事务；失败可重试；重启可恢复 | MySQL仓储与集成测试 | 真实资源库暂未建设 |
| UI-C01 | 接口与交互支线 | 流式对话页面 | `AgentController.java`、`agentApi.ts`、`agent.ts` | 用户自然语言 | SSE消息与阶段 | 基线已完成 | 流式拼接；失败丢弃半成品 | Controller与前端测试 | 无 |
| UI-C02 | 接口与交互支线 | 交通卡片与顶部应急告警 | `TrafficResultPanel.vue`、`EmergencyAlertCard.vue`、应急Store | 交通结果、待处理事件、最新工单 | 非阻塞告警、生成、审批、返工和无需调度操作 | 已完成 | 页面可见时5秒轮询；二次确认；处理后切换下一事件；审批防重复点击 | 交通与应急前端测试 | warning需中文化 |

状态建议统一使用：`待领取`、`进行中`、`待评审`、`已完成`、`受阻`。
