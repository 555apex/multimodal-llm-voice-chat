# 开发进度与版本记录

## 2026-09-12：解除预案资源限制并补强生成恢复边界

- 修正道路通行能力分级阈值：利用率严格大于60%判定为瓶颈，严格大于80%判定为严重瓶颈，替换上一版偏低的20%/30%阈值。
- 应急预案只约束事件类型和处置正文；历史 `resource_baseline` 可为空且只作为旧版本快照，不再作为资源白名单或必选清单。
- 模型可按事件事实和人工返工意见，从全部启用的数据库资源目录选择资源；Java继续负责库存锁定、同城优先、跨市距离、最低保有量和缺口计算，不允许模型编造库存事实。
- 对模型语义输出执行一次带具体原因的自动修正，覆盖目录外编码、重复类型、非法数量、空需求及正文校验；第二次仍失败时落为可重试失败，不进入库存事务。
- 失败重试会恢复同一方案版本和原返工意见；若冻结预案与事件类型不匹配，则在进入生成态前改用该事件类型的当前已发布预案，避免异常快照形成不可恢复状态。
- 定向测试覆盖预案外照明资源、资源历史适用标签不一致、语义修正成功、未知数据库资源不占库存及预案无资源基线。
- 人工审阅Word已改为16类“需核实信息 + 简明处置预案”，移除全部资源基线表格，完成17页渲染检查。
- 已对共享 `road_agent` schema 执行 `20260912_publish_resource_unbound_response_plans.sql`，16类活动预案均发布为v3且 `resource_baseline=[]`；原v1/v2、工单、通告、库存和占用历史保留。

## 2026-09-10：未办结通告卡片直接归还资源

- 未办结摘要卡片新增“归还全部资源”按钮，不展开详情即可填写原因并二次确认；展开时隐藏摘要入口，仅显示详情入口，收起后恢复，避免同时出现两个按钮。
- 卡片操作前按需读取最新归还资格与工作流版本，两个入口复用同一提交锁、幂等重试和归还接口；后台轮询不禁用按钮，失败保留输入，版本冲突后重新确认。
- 本次只修改前端及测试，未操作数据库或改动后端接口。前端82项测试与生产构建通过，未打开浏览器。
- 本次“数据库更改”版本发布整理另行完成通告、预案、车型日期和容量相关的前端33项、后端76项定向测试，以及SQL模板、OpenAPI、文档路径和敏感信息静态检查；未执行共享库脚本、Docker、真实模型或浏览器验收。

## 2026-09-10：简明预案与下发通告

- 提供16类约350字简明预案的新版本发布SQL，沿用原事实项和资源基线，保留历史版本与通告快照；本次未操作共享数据库。
- 填充变量控制为完整短句，未知事实使用完整兜底句；处理变量边界重复标点，保留小数及道路桩号。简明版正文校验300–400字，超长最多请求一次修正，校验位于库存事务前。
- 新增通告摘要分页接口，MySQL按最终版本的实际DISPATCHED占用筛选未办结，归还后及无需调度记录进入已办结；旧history接口保留。
- 前端按需展开详情，轮询保留当前筛选和分页，过期响应丢弃；归还独立忙状态、二次确认、失败保留原因、幂等重试与冲突刷新。
- 已通过Maven全量测试、前端78项测试及生产构建；真实数据库、真实模型和浏览器未执行验收。

## 2026-09-09：设施预警与应急生成中断修复

- 设施告警 `BIGINT` ID 从 REST、OpenAPI 到前端统一改为十进制字符串，避免浏览器整数舍入导致处置错位；所有读取、统计和条件更新补齐逻辑删除过滤。
- 设施页面以请求版本丢弃过期轮询响应，处置表单打开期间锁定筛选切换，提交失败时在原表单内保留错误和用户输入。
- 应急方案生成状态超过120秒时，页面针对同一方案版本只自动恢复一次，并保留人工“恢复生成”入口；后端沿用已有超时认领、条件更新、乐观锁和重试审计。
- 同步调整模拟数据的通行能力高利用率瓶颈口径，并区分ASR无有效语音与真实服务故障。
- README新增面向已部署 `61e64d4` 服务器的非破坏性快速升级说明和可直接交给Codex执行的工作计划；本版不要求新增SQL或重建语音容器。
- 定向验证前端7个相关测试文件共51项、后端10个相关测试类共62项通过；按发布约束未运行Docker、真实数据库、真实模型、浏览器验收或全量测试。

## 2026-09-08：协作者说明与发布整理

- README将全新Demo库基础初始化、已有共享库升级和普通协作者直接连接三种情况分开说明，并补入 `20260904_fix_incident_id_collation.sql` 的执行位置。
- 增加设施预警健康检查、自动事件分类开关、迁移脚本写入风险、模块说明和排障入口；保留 Maven Wrapper `only-script` 说明及真实密钥不入库要求。
- 本次发布按用户要求只做差异、契约、文档路径、敏感信息、忽略规则和大文件等静态检查，不运行Docker、数据库、真实模型、浏览器或整套构建测试；既有测试代码与历史验收记录随版本保留。

## 2026-09-08：设施健康预警与处置闭环

- 新增“设施预警”第三功能入口，默认展示待确认异常，支持按处理状态和告警等级筛选、5秒轮询、分页和待确认角标。
- 仅接入 `w_realtime_abnormal`，展示设施名称、异常指标、实际值或状态值、阈值快照、等级与时间；阈值计算、历史比较和写入异常由上游协作系统负责。
- Java确定性生成健康状态报告、风险预警清单和重点关注对象，重点对象依次按最高等级、活动告警数、最早触发时间排序，不调用模型。
- 处置状态严格为待确认→处理中→已结束，结束区分已消除/已忽略；使用原状态条件更新防止并发覆盖，`remark` 保存最新处理结果。
- 补充Core汇总/状态机、H2 MySQL适配器、REST接口和前端功能入口测试；OpenAPI增加四个设施预警接口。
- 本期不含气象数据联动；当前表无地点与设施类型字段，页面使用 `facility_name` 作为设施识别信息。

## 2026-09-08：需求1-7城市目的地联系倾向重构

- 需求1-7改为复用 `MysqlRegionalTrafficRepository / OdTrafficService`，根据 `w_highway_network` 起终城市确定无方向城市对，使用同 `route_code` 卡口的 `temp_2` 形成路线代表联系强度。运行时不再使用卡口 `region_code`、`w_route_city_mapping` 和 `w_city_od_connection_result`。
- 同一路线的卡口7日流量先求均值，城市对强度再对相关路线求和。单城市目的地倾向以该城市在全省跨市网络中的全部联系强度为固定分母。
- 查询收敛为 `OD_DESTINATION_TENDENCY` 和 `OD_CONNECTION_MATRIX`：前者展示单城市目的地排名，后者展示2至9市或全省矩阵。前端、REST/SSE、意图、开场白、建议问法和ASR热词同步调整。
- 摘要只使用“目的地联系倾向”等定性表述，不宣称真实车辆去向、行驶方向、净流入流出或OD概率。1-5继续表达城市对/路线绝对压力，与1-7的相对结构分析保持边界。

## 2026-09-04：节假日及重大活动交通背景接入

- 新增 `w_festival_data`，共享MySQL已实际创建并写入33条有效数据：2026年正式节假日/调休13条、2027—2028法定日期14条、模拟演出和赛事6条。
- 路况服务按交通快照北京时间即时读取生效事件，仅在 `status>=20` 且省、市或路线范围匹配时生成谨慎原因句；节假日表不参与30秒快照等待，也不改变数据库路况状态和短时趋势标签。
- 全省、拥堵异常、两市路况、指定G/S路线均支持该原因提示；模型继续禁止猜测事故、施工和天气，原因句由Java确定性生成并置于当前态势与未来1至2小时趋势之间。
- 补充节假日仓储、时区、启停、非法记录、范围匹配、正常路况不归因和原因问法测试；辅助表读取失败时保留核心路况回答。

## 2026-09-04：w_lw_incident应急业务迁移与自动分类

- `w_lw_incident` 成为唯一事件数据源，全链路使用 `c_no VARCHAR(64)`；旧 `w_abnormal_event` 保留一个版周期供回滚，运行时不再读取。
- 新增规则优先、模型严格JSON兜底的16类事件分类，每5秒轮询，失败留痕和退避重试；低置信度的有效单选结果仍采用。
- 新增一级人工类型更正：释放旧资源、驳回旧方案、修改贴源 `event_type`、保存分类/动作流水并按新类型生成下一版。
- 事件城市从来源、所属单位、县区、描述、经纬度依次推导，不回写贴源表；来源、地点和路线已在一级卡片显示。
- 展示与模型使用的贴源事件事实字段止于 `route_name`；`post`、人员和处置单位字段不进入项目，`status/completed/deleted` 仅用于系统过滤与完成回写。`content` 内嵌的联系人和联系电话在适配层清理后再进入前端、分类与方案生成。
- 补齐中断恢复闭环：生成状态超过120秒时，前端只自动恢复一次，并提供人工“恢复生成”入口；后端继续以条件更新和乐观锁保证共享数据库多实例下不会重复认领。
- 共享MySQL已实际迁移并核验：42条事件、39条应急待办、3条非应急对照，工单/工作流/资源占用已清空，19条旧事件保留并恢复未处理。
- 新增九市道路巡查、机电抢修、除雪作业车和融雪防滑物资，并为既有13类资源补齐16类事件适用关系。
- 已将审阅后的16类Word预案转换为 `w_emergency_response_plan` v1；运行时按事件类型读取唯一已发布版本，模型仅填充变量和有限补充，工单/通告冻结预案快照。
- `w_emergency_resource` 增加九市中心经纬度；移除资源距离适配器中的坐标常量，由MySQL提供坐标、Java Haversine公式计算距离。

## 2026-09-04：用户视角问答边界收敛

- 新增受控路线目录、项目口径解释和针对性边界答复；数据来源、更新频率、底层字段与内部治理继续不对用户开放。
- 普通当前路况不再强制附加趋势；拥堵、异常或明确趋势问法才返回未来1至2小时定性研判。路线名称使用活动目录匹配，最近一次成功交通查询以结构化上下文保留一小时。
- 需求1-5已重构为福建3至9市跨区域交通联系分析：路网起终点建立无方向城市对，并按路线关联卡口7日流量；两市容量仍按路线目录登记起终点双向匹配后关联整条路线容量；福州和厦门车型查询支持同一回答两张独立结果卡。
- 补充缺小时补0语义提示、中国时区数据时间和区域综合摘要三维覆盖校验；不执行共享数据库写操作。

## 2026-09-03：需求1-7城市OD七日卡口统计

> 本节为历史实现记录，运行时已由2026-09-08的目的地联系倾向方案替代。

- 新增独立 `MysqlOdTrafficRepository / OdTrafficService`，读取 `temp_2` 七日总量和 `temp_3.car/bus/truck` 车型总量，按所选城市卡口并集统计，不接入旧路线城市映射或城市对OD结果表。
- 新增 `OD_OVERVIEW / OD_CITY_FLOW / OD_KEY_CHANNELS` 三类意图，支持1至9市；1-5收敛为城市对与跨市路线两个展示层级，1-6保留单城市车型分析。REST、SSE、开场白、建议问法和ASR热词同步扩展。
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
| TQ-A01 | Agent与业务主线 | MySQL国省干线交通Skill、事件原因提示与短时趋势 | `IntentPlanner.java`、`HighwayTrafficSkill.java`、`HighwayTrafficService.java` | 全省、异常、两市、G/S路线 | 当前研判 + 已验证节假日/活动原因提示 + 未来1–2小时定性趋势 + 确定性表格 | 已完成 | 四种路况查询、五级状态、事件时空范围匹配、五种趋势标签、状态优先、模型失败无半成品 | `IntentPlannerTest`、`HighwayTrafficServiceTest`、`HighwayTrafficSkillTest` | 事件原因是谨慎的叠加影响研判，不代表唯一因果 |
| TQ-B03 | 数据与Tool支线 | 节假日及重大活动即时只读仓储 | `MysqlTrafficContextEventRepository.java`、`w_festival_data` | 交通快照时间、事件时间与影响范围 | 最多3条已验证交通背景事实 | 已完成 | 北京时间边界、启停/逻辑删除、城市/路线范围、单条非法数据隔离、辅助表失败不阻断路况 | `MysqlTrafficContextEventRepositoryTest`、`HighwayTrafficServiceTest` | 2027—2028完整调休安排需待官方通知后维护 |
| CP-A01 | Agent与业务主线 | 通行能力与瓶颈路线研判 | `RoadCapacityService.java`、`HighwayTrafficSkill.java` | 全省能力、瓶颈排行、G/S路线 | 专业研判 + 容量表格 | 已完成 | 三级阈值、数据库值不重算、瓶颈前10、不依赖固定句式或“表格”关键词、越界数字使用事实摘要 | `RoadCapacityServiceTest`、`CapacityLevelTest`、前端表格测试 | 无 |
| RT-A01 | Agent与业务主线 | 跨区域交通联系分析（需求1-5） | `MysqlRegionalTrafficRepository.java`、`RegionalTrafficService.java`、`HighwayTrafficSkill.java` | 路网路线起终点城市对 + 同路线卡口 | 模型总结 + 城市对Top5/跨市路线Top10 | 已完成 | 3至9市或全省、7日流量排序、无方向、禁止真实OD结论、卡口仅用于路线聚合 | 区域仓储/服务/意图/前端测试 | 无 |
| VP-A01 | Agent与业务主线 | 车型出行特征分析（需求1-6） | `VehiclePatternService.java`、`VehiclePatternCharts.vue` | 福州或厦门当天/指定历史日期记录 | 车型/时间/日类型表格和三类图表 | 已完成 | 默认当天、支持昨天/前天/明确日期、保留记录内有效小时点、缺小时补0并说明、工作日周末口径、按问题选择视图 | 车型仓储/服务/前端测试 | 当前仅福州和厦门测试数据 |
| ED-A01 | Agent与业务主线 | 三级应急状态机与通告 | `EmergencyWorkflow.java`、`DispatchApplicationService.java` | 事件、版本方案、三级决策 | 市级专业复核、省级决策通告和全程流水 | 已完成 | 禁止越级；三级批准前贴源事件保持 `status='1', completed=0`；任一级退回生成新版并重走三级 | `EmergencyWorkflowTest`、`DispatchApplicationServiceTest` | 真实身份权限与外部通知平台未接入 |
| TQ-B01 | 数据与Tool支线 | MySQL三表只读仓储 | `MysqlHighwayTrafficSnapshotSource.java` | 三张交通表 | 不可变交通快照 | 已完成 | 逻辑删除、五级状态、业务路线为活动路网子集、唯一性/范围校验 | `MysqlHighwayTrafficSnapshotSourceTest` | 无 |
| TQ-B02 | 数据与Tool支线 | 一致性快照原子发布 | `MysqlHighwayTrafficSnapshotSource.java`、`InMemoryHighwayTrafficSnapshotCache.java` | 5秒候选快照 | 冷启动首份合法快照 + 稳定后更新 | 已完成 | 一致性事务内取数和生成指纹；冷启动立即可用；运行期保留旧快照并稳定30秒后切换 | `MysqlHighwayTrafficSnapshotSourceTest`、`InMemoryHighwayTrafficSnapshotCacheTest` | 无数据库批次号，无法识别协作者定义的业务批次边界 |
| CP-B01 | 数据与Tool支线 | 通行能力独立只读快照 | `MysqlRoadCapacitySnapshotSource.java`、`InMemoryRoadCapacitySnapshotCache.java` | `w_road_capacity`与活动路网 | 原子发布的路线容量快照 | 已完成 | 当前有数据路线为活动路网子集、名称/唯一性/范围校验、旧批次快速可用且不影响路况 | 容量仓储、快照与共享MySQL只读集成测试 | 无数据库批次号，无法做绝对一致 |
| RT-B01 | 数据与Tool支线 | 跨区域路线卡口即时只读仓储 | `MysqlRegionalTrafficRepository.java` | `w_highway_network`、`w_transport_hubs` | 一致性事务路线—城市对—卡口事实 | 已完成 | 路线与卡口唯一、同城/非法路线排除、异常行跳过、路网名称权威 | H2与共享MySQL只读测试 | 不经过30秒快照等待，不使用卡口region_code |
| VP-B01 | 数据与Tool支线 | 最新车型记录只读仓储 | `MysqlVehicleTravelPatternRepository.java` | `w_vehicletravelpatternanalyzer` | 单城市最新结构化记录 | 已完成 | `create_time,id`排序、JSON和非负校验、车型完整性 | H2与共享MySQL只读测试 | `result4/result5`未使用 |
| ED-B01 | 数据与Tool支线 | MySQL事件、方案快照与三级留痕 | `AbnormalEventRepository.java`、`MysqlDispatchRepository.java`、`MysqlEmergencyWorkflowRepository.java`、`SpringUnitOfWork.java` | 待处理事件与三级动作 | 方案历史、会商、决策、通告和时间线 | 已完成 | 每事件唯一活动流程；乐观锁和幂等；通告同事务办结；事件快照不受源表后续修改影响 | 三级MySQL仓储与H2适配器测试 | 真实人员和通知平台未接入 |
| ED-B02 | 数据与Tool支线 | 福建九市库存资源调度 | `EmergencyResourceAllocator.java`、`MysqlEmergencyResourceRepository.java`、`MysqlResourceAllocationRepository.java`、`FujianCityDistanceAdapter.java` | 受限资源需求、事件城市和最新库存 | 实际分配、来源城市、距离和明确缺口 | 已完成 | 同城优先；近城补足；保留最低库存；不超卖；返工释放；批准调度；整单归还 | `EmergencyResourceAllocatorTest`、`DispatchApplicationServiceTest`、`EmergencyResourceWorkflowIntegrationTest`、前端资源展示/归还测试 | 当前库存均为Demo，需甲方真实资源接口替换 |
| UI-C01 | 接口与交互支线 | 流式对话页面 | `AgentController.java`、`agentApi.ts`、`agent.ts` | 用户自然语言 | SSE消息与阶段 | 基线已完成 | 流式拼接；失败丢弃半成品 | Controller与前端测试 | 无 |
| UI-C02 | 接口与交互支线 | 三级待办、会商表、通告和流程记录 | `EmergencyWorkflowController.java`、`AgentDrawer.vue`、`EmergencyAlertCard.vue`、`WorkflowHistoryPanel.vue`、应急Store | 三级待办和人工表单 | 流转结果、只读通告和时间线 | 已完成 | 页面可见时5秒轮询；三级数量和视角切换；各级表单校验；聊天不被阻塞 | Controller、前端组件、Store和生产构建 | Demo视角不具备真实权限防护 |
| FH-A01 | Agent与业务主线 | 设施健康状态与重点对象汇总 | `FacilityAlertService.java` | `w_realtime_abnormal` 活动告警 | 健康报告、重点关注列表 | 已完成 | Java确定性计数/分级/排序；空表不声称全量健康 | `FacilityAlertServiceTest` | 气象联动与设施地点/类型待数据源扩展 |
| FH-B01 | 数据与Tool支线 | 设施异常只读与条件回写 | `MysqlFacilityAlertRepository.java` | 告警筛选、状态流转 | 预警快照、数量、状态/备注 | 已完成 | 常规查询仅SELECT；处置仅UPDATE status/remark；并发状态冲突不覆盖 | `MysqlFacilityAlertRepositoryTest` | 上游阈值合理性由协作系统负责 |
| FH-C01 | 接口与交互支线 | 设施预警页签与处置 | `FacilityAlertController.java`、`FacilityWarningPanel.vue`、设施Store | 预警分页和处置说明 | 预警清单、健康报告、重点对象 | 已完成 | 第三功能入口；5秒轮询；三阶段处置；移动端布局 | Controller、App集成测试和生产构建 | 暂无真实身份/操作人字段 |

状态建议统一使用：`待领取`、`进行中`、`待评审`、`已完成`、`受阻`。
