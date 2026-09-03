# 开发进度与版本记录

## 2026-09-03：需求1-7城市OD七日卡口统计

- 新增独立 `MysqlOdTrafficRepository / OdTrafficService`，读取 `temp_2` 七日总量和 `temp_3.car/bus/truck` 车型总量，按所选城市卡口并集统计，不接入旧路线城市映射或城市对OD结果表。
- 新增 `OD_OVERVIEW / OD_CITY_FLOW / OD_KEY_CHANNELS` 三类意图，支持1至9市；1-5保留原三类排名，1-6保留单城市车型分析。REST、SSE、开场白、建议问法和ASR热词同步扩展。
- 前端增加“城市区域流量不平衡”“城市交通关键OD通道”两张表，路线折叠10条/展开全部，移动端可横向查看，摘要先于表格，语音仅摘要。
- 不要求九市/48路线齐全、不强制更新时间相同、不增加稳定等待；0有效、缺失城市不补零；7日与日均量分别取源值，车型合计差异提示而不修正。
- 新增仓储、业务、意图、REST、SSE发布顺序、前端和共享MySQL只读集成回归。历史草稿SQL不执行，现有应急、路况、容量、语音业务保持兼容。
- 既有开发验收记录（保留原记录，本次版本整理未重新执行）：后端 `./mvnw package` 成功（168项，151通过、17项按外部环境条件跳过）；前端59项测试及生产构建通过；OpenAPI YAML解析通过。另行启用并通过OD共享MySQL只读集成测试，以及真实模型的构造数据摘要、增补城市、第二张表切换和返回1-5问答测试。该轮记录注明未启停用户的服务进程或修改共享数据库。
- 本次发布整理：补齐README中的OD简介、18种查询能力、现有表字段要求与快速启动指引，明确两份20260902 SQL仅为未采用的历史草稿；按锁文件修正Node版本要求，保留only-script Maven Wrapper说明。仅进行差异、敏感信息、文件/链接及接口契约静态检查，不运行Docker、数据库、真实模型、浏览器或整套测试/构建。

建议团队在例会中维护此表。一个任务只对应一个可验收结果，不要把“完成整个Agent”写成一行。

| 编号 | 开发角色 | 业务功能 | 模块/实现类 | 输入 | 输出 | 当前状态 | 完成标准 | 测试 | 阻塞项 |
|---|---|---|---|---|---|---|---|---|---|
| AG-A01 | Agent与业务主线 | 意图识别与参数提取 | `IntentPlanner.java`、`KnownTrafficQuestionClassifier.java` | 用户消息、会话历史 | `AgentDecision` | 已完成 | 仅允许三种意图；18类交通标准问法及常见变体确定性识别；模糊表达交由模型；非法JSON修复一次 | `IntentPlannerTest`、`OdIntentPlannerTest`、`AgentRuntimeTest`、模型适配器测试 | 需随真实用户语料持续扩充问法回归集 |
| AG-A02 | Agent与业务主线 | Skill白名单与运行时 | `AgentRuntime.java`、`SkillRegistry.java` | `AgentDecision` | SSE阶段与业务结果 | 基线已完成 | 未注册能力不可执行；失败发`run.failed` | `AgentRuntimeTest` | 无 |
| TQ-A01 | Agent与业务主线 | MySQL国省干线交通Skill与短时趋势 | `IntentPlanner.java`、`HighwayTrafficSkill.java`、`HighwayTrafficService.java` | 全省、异常、两市、G/S路线 | 3–4句当前研判 + 1句未来1–2小时定性趋势 + 确定性表格 | 已完成 | 四种路况查询、五级状态、五种趋势标签、状态优先、趋势无精确数值或原因、模型失败无半成品 | `IntentPlannerTest`、`HighwayTrafficServiceTest`、`HighwayTrafficSkillTest` | 当前事实启发式研判，不是精确预测模型 |
| CP-A01 | Agent与业务主线 | 通行能力与瓶颈路线研判 | `RoadCapacityService.java`、`HighwayTrafficSkill.java` | 全省能力、瓶颈排行、G/S路线 | 专业研判 + 容量表格 | 已完成 | 三级阈值、数据库值不重算、瓶颈前10、不依赖固定句式或“表格”关键词、越界数字使用事实摘要 | `RoadCapacityServiceTest`、`CapacityLevelTest`、前端表格测试 | 无 |
| RT-A01 | Agent与业务主线 | 区域卡口交通压力分析（需求1-5） | `RegionalTrafficService.java`、`HighwayTrafficSkill.java` | 全省或一至两市卡口并集 | 模型总结 + 卡口Top20/城市Top5/路线Top10 | 已完成 | 活跃阈值、城市范围、稳定排序、禁止OD流向结论、缺失或异常城市解读安全补齐 | 区域仓储/服务/前端测试 | 无 |
| VP-A01 | Agent与业务主线 | 车型出行特征分析（需求1-6） | `VehiclePatternService.java`、`VehiclePatternCharts.vue` | 福州或厦门最新记录 | 车型/时间/日类型表格和三类图表 | 已完成 | 按城市最新、缺小时补0、工作日周末口径、按问题选择视图 | 车型仓储/服务/前端测试 | 当前仅福州和厦门测试数据 |
| ED-A01 | Agent与业务主线 | 三级应急状态机与通告 | `EmergencyWorkflow.java`、`DispatchApplicationService.java` | 事件、版本方案、三级决策 | 市级专业复核、省级决策通告和全程流水 | 已完成 | 禁止越级；三级前事件仍为0；任一级退回生成新版并重走三级 | `EmergencyWorkflowTest`、`DispatchApplicationServiceTest` | 真实身份权限与外部通知平台未接入 |
| TQ-B01 | 数据与Tool支线 | MySQL三表只读仓储 | `MysqlHighwayTrafficSnapshotSource.java` | 三张交通表 | 不可变交通快照 | 已完成 | 逻辑删除、五级状态、业务路线为活动路网子集、唯一性/范围校验 | `MysqlHighwayTrafficSnapshotSourceTest` | 无 |
| TQ-B02 | 数据与Tool支线 | 一致性快照原子发布 | `MysqlHighwayTrafficSnapshotSource.java`、`InMemoryHighwayTrafficSnapshotCache.java` | 5秒候选快照 | 冷启动首份合法快照 + 稳定后更新 | 已完成 | 一致性事务内取数和生成指纹；冷启动立即可用；运行期保留旧快照并稳定30秒后切换 | `MysqlHighwayTrafficSnapshotSourceTest`、`InMemoryHighwayTrafficSnapshotCacheTest` | 无数据库批次号，无法识别协作者定义的业务批次边界 |
| CP-B01 | 数据与Tool支线 | 通行能力独立只读快照 | `MysqlRoadCapacitySnapshotSource.java`、`InMemoryRoadCapacitySnapshotCache.java` | `w_road_capacity`与活动路网 | 原子发布的路线容量快照 | 已完成 | 当前有数据路线为活动路网子集、名称/唯一性/范围校验、旧批次快速可用且不影响路况 | 容量仓储、快照与共享MySQL只读集成测试 | 无数据库批次号，无法做绝对一致 |
| RT-B01 | 数据与Tool支线 | 区域卡口即时只读仓储 | `MysqlRegionalTrafficRepository.java` | `w_transport_hubs`、`w_region_code` | 一致性事务卡口快照 | 已完成 | 逻辑删除、区域映射、卡口唯一、非负数值、路线名一致 | H2与共享MySQL只读测试 | 不经过30秒快照等待 |
| VP-B01 | 数据与Tool支线 | 最新车型记录只读仓储 | `MysqlVehicleTravelPatternRepository.java` | `w_vehicletravelpatternanalyzer` | 单城市最新结构化记录 | 已完成 | `create_time,id`排序、JSON和非负校验、车型完整性 | H2与共享MySQL只读测试 | `result4/result5`未使用 |
| ED-B01 | 数据与Tool支线 | MySQL事件、方案快照与三级留痕 | `AbnormalEventRepository.java`、`MysqlDispatchRepository.java`、`MysqlEmergencyWorkflowRepository.java`、`SpringUnitOfWork.java` | 待处理事件与三级动作 | 方案历史、会商、决策、通告和时间线 | 已完成 | 每事件唯一活动流程；乐观锁和幂等；通告同事务办结；事件快照不受源表后续修改影响 | 三级MySQL仓储与H2适配器测试 | 真实人员和通知平台未接入 |
| ED-B02 | 数据与Tool支线 | 福建九市库存资源调度 | `EmergencyResourceAllocator.java`、`MysqlEmergencyResourceRepository.java`、`MysqlResourceAllocationRepository.java`、`FujianCityDistanceAdapter.java` | 受限资源需求、事件城市和最新库存 | 实际分配、来源城市、距离和明确缺口 | 已完成 | 同城优先；近城补足；保留最低库存；不超卖；返工释放；批准调度；整单归还 | `EmergencyResourceAllocatorTest`、`DispatchApplicationServiceTest`、`EmergencyResourceWorkflowIntegrationTest`、前端资源展示/归还测试 | 108条数据为Demo，需甲方真实资源接口替换 |
| UI-C01 | 接口与交互支线 | 流式对话页面 | `AgentController.java`、`agentApi.ts`、`agent.ts` | 用户自然语言 | SSE消息与阶段 | 基线已完成 | 流式拼接；失败丢弃半成品 | Controller与前端测试 | 无 |
| UI-C02 | 接口与交互支线 | 三级待办、会商表、通告和流程记录 | `EmergencyWorkflowController.java`、`AgentDrawer.vue`、`EmergencyAlertCard.vue`、`WorkflowHistoryPanel.vue`、应急Store | 三级待办和人工表单 | 流转结果、只读通告和时间线 | 已完成 | 页面可见时5秒轮询；三级数量和视角切换；各级表单校验；聊天不被阻塞 | Controller、前端组件、Store和生产构建 | Demo视角不具备真实权限防护 |

状态建议统一使用：`待领取`、`进行中`、`待评审`、`已完成`、`受阻`。
