# 开发进度表示例

建议团队在例会中维护此表。一个任务只对应一个可验收结果，不要把“完成整个Agent”写成一行。

| 编号 | 开发角色 | 业务功能 | 模块/实现类 | 输入 | 输出 | 当前状态 | 完成标准 | 测试 | 阻塞项 |
|---|---|---|---|---|---|---|---|---|---|
| TQ-A01 | Agent与业务主线 | 交通查询固定Skill | `RealtimeTrafficSkill.java` | `TrafficQueryCommand` | `TrafficQueryResult` | 基线已完成 | 固定5步；模型失败可降级 | `RealtimeTrafficSkillTest` | 无 |
| TQ-A02 | Agent与业务主线 | 规则摘要优化 | `RuleBasedTrafficSummaryGenerator.java` | `TrafficSnapshot` | 中文摘要 | 待领取 | 统计不同拥堵等级且不编造事实 | 新增单元测试 | 无 |
| TQ-B01 | 数据与Tool支线 | 实时路况查询Tool | `QueryRealtimeTrafficTool.java` | `TrafficQuery` | `TrafficSnapshot` | 基线已完成 | 仅依赖 `TrafficDataPort` | 适配器测试 | 等待真实数据接口 |
| TQ-B02 | 数据与Tool支线 | 高德数据映射 | `AmapTrafficDataAdapter.java` | 高德JSON | 内部标准路况 | 基线已完成 | 状态、速度和错误正确映射 | `AmapTrafficDataAdapterTest` | Key可能无高级服务权限 |
| TQ-B03 | 数据与Tool支线 | Mock部分数据场景 | `MockTrafficDataAdapter.java` | 查询条件 | 部分路段 | 待领取 | 可通过配置切换且有测试 | 新增适配器测试 | 无 |
| TQ-C01 | 接口与交互支线 | 交通查询表单 | `TrafficQueryForm.vue` | 用户输入 | 查询请求 | 基线已完成 | 有本地校验和加载状态 | 组件测试 | 无 |
| TQ-C02 | 接口与交互支线 | 警告中文化 | `TrafficResultPanel.vue` | warning code | 中文提示 | 待领取 | 空、旧、降级提示可区分 | 新增组件测试 | 需共同确定文案 |

状态建议统一使用：`待领取`、`进行中`、`待评审`、`已完成`、`受阻`。
