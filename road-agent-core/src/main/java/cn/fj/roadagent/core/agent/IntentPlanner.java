package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

import java.util.ArrayList;
import java.util.List;

/** 模型提出计划，Java负责检查意图白名单和必填参数。 */
/*
  Java（白名单）的权限：
  1. 系统Prompt里限定只允许3种意图和3种范围 → 大模型选不出别的
  2. FujianCity枚举限定只有9个城市 → 不是这9个城市就被拒绝
  3. missingFields() 检查必填参数 → 少一个就追问用户
  4. SkillRegistry 的 EnumMap 限定只有注册过的Skill能被执行 → 大模型建议执行一个不存在的Skill时直接报错
 */

public final class IntentPlanner {

    private final ChatModelPort chatModelPort;  // 大模型端口

    public IntentPlanner(ChatModelPort chatModelPort) {
        this.chatModelPort = chatModelPort;
    }

    // 方法：用户信息发送给大模型理解
    public AgentDecision plan(String currentMessage, List<ConversationMessage> history) {
        String systemPrompt = """
                你是福建公路应急交通Agent的意图规划器。
                只能选择TRAFFIC_QUERY、EMERGENCY_DISPATCH、UNSUPPORTED之一。
                TRAFFIC_QUERY支持具体道路和福建省内市、区、县的区域交通态势。
                EMERGENCY_DISPATCH用于道路塌方、事故、水毁等事件的资源调度。
                仅提取用户明确提供或会话中已有的信息，不得编造城市、道路、位置和资源。
                必须输出json对象，字段如下：
                intent, trafficScope, city, areaName, roadName, direction, eventType, location, severity,
                eventDescription, resourceTypes, clarification。
                trafficScope只能为ROAD、AREA_ALL、AREA_MAJOR：
                - 明确指定一条道路时使用ROAD；
                - 询问城市、区或县整体交通时使用AREA_ALL；
                - 出现交通要道、主要道路、主干道、重点道路时使用AREA_MAJOR。
                AREA查询不得凭自身知识列举道路，只提取用户说出的行政区名称。
                不适用字段使用null，resourceTypes使用字符串数组。
                缺少必填信息时，clarification写一条简短中文追问。
                """.strip();
        ModelRequest request = new ModelRequest(
                systemPrompt,   // 系统提示词
                currentMessage, // 当前文本输入信息
                toModelMessages(history),   // 历史对话（上下文）
                0.2 // temperature,输出随机性
        );
        // 返回结构化JSON，并映射到AgentDecision.class
        return chatModelPort.generateStructured(request, AgentDecision.class);
    }

    // 方法：Java二次校验，对模型返回结果逐项检查必填参数
    public List<String> missingFields(AgentDecision decision) {
        List<String> missing = new ArrayList<>();
        if (decision.parsedIntent() == AgentIntent.TRAFFIC_QUERY) {
            var scope = decision.parsedTrafficScope();
            if (scope.isEmpty()) {
                missing.add("trafficScope");    // 缺失范围
            } else if (scope.get() == TrafficQueryScope.ROAD) {
                if (FujianCity.fromName(decision.city()).isEmpty()) {
                    missing.add("city");        // 缺失城市名
                }
                if (isBlank(decision.roadName())) {
                    missing.add("roadName");    // 缺失路名
                }
            } else if (isBlank(decision.areaName()) && isBlank(decision.city())) {
                missing.add("areaName");        // 缺失区域查询
            }
        }
        if (decision.parsedIntent() == AgentIntent.EMERGENCY_DISPATCH) {
            if (FujianCity.fromName(decision.city()).isEmpty()) {
                missing.add("city");
            }
            if (isBlank(decision.location())) {
                missing.add("location");
            }
            if (isBlank(decision.eventDescription())) {
                missing.add("eventDescription");
            }
        }
        return missing;
    }

    // 方法：(application层)ConversationMessage 转成 (application层)ModelMessage
    private List<ModelMessage> toModelMessages(List<ConversationMessage> history) {
        return history.stream()
                .map(message -> new ModelMessage(message.role(), message.content()))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
