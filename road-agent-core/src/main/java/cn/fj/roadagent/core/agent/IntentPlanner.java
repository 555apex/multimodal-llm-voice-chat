package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

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
        var deterministic = KnownTrafficQuestionClassifier.classify(currentMessage);
        if (deterministic.isPresent()) {
            return deterministic.get();
        }
        String systemPrompt = """
                你是福建公路应急交通Agent的意图规划器。
                只能选择TRAFFIC_QUERY、EMERGENCY_DISPATCH、UNSUPPORTED之一。
                TRAFFIC_QUERY支持福建省普通国道、省道及交调站划分路段的交通状态、国省道通行能力、区域卡口交通压力，以及福州或厦门的车型出行特征分析；不支持城市道路、区县道路和高速公路。
                EMERGENCY_DISPATCH用于道路塌方、事故、水毁等事件的资源调度。
                仅提取用户明确提供或会话中已有的信息，不得编造城市、道路、位置和资源。
                必须输出json对象，字段如下：
                intent, trafficScope, originCity, destinationCity, routeCode, routeName, selectedCities, analysisCity,
                city, areaName, roadName, direction, eventType, location, severity,
                eventDescription, resourceTypes, clarification。
                selectedCities和resourceTypes使用字符串数组，其他不适用字段使用null。
                trafficScope只能为PROVINCE_OVERVIEW、PROVINCE_ABNORMAL、CITY_PAIR、ROUTE_DETAIL、CAPACITY_OVERVIEW、CAPACITY_BOTTLENECKS、CAPACITY_ROUTE_DETAIL、REGIONAL_TRAFFIC_OVERVIEW、CHECKPOINT_PRESSURE、CITY_PRESSURE、ROUTE_PRESSURE、VEHICLE_PATTERN_OVERVIEW、VEHICLE_STRUCTURE、VEHICLE_HOURLY_PATTERN、VEHICLE_DAY_TYPE_COMPARISON：
                - 询问福建省整体、全省国省道交通态势时使用PROVINCE_OVERVIEW；
                - 询问福建省哪些路段拥堵、异常或最拥堵时使用PROVINCE_ABNORMAL；
                - 询问两个福建地级市之间交通情况时使用CITY_PAIR，并分别提取originCity、destinationCity；
                - 指定G/S路线编号或国省道路线名称时使用ROUTE_DETAIL，优先提取routeCode，否则提取routeName。
                - 询问全省各国省道实际通行能力、设计通行能力或利用率总览时使用CAPACITY_OVERVIEW；
                - 询问全省哪些路线是瓶颈、严重瓶颈或通行能力利用率最低时使用CAPACITY_BOTTLENECKS；
                - 询问指定G/S路线的实际通行能力、设计通行能力、利用率或瓶颈等级时使用CAPACITY_ROUTE_DETAIL，优先提取routeCode，否则提取routeName。
                - 询问区域交通联系、区域交通压力综合情况或同时关注卡口/城市/路线压力时使用REGIONAL_TRAFFIC_OVERVIEW；用户明确提到的一至两个城市写入selectedCities，未指定城市时为空数组；两个城市表示合并两市卡口范围，不是OD查询。
                - 询问哪些卡口、交调站或交通枢纽日均流量大、压力大时使用CHECKPOINT_PRESSURE；城市筛选仍写入selectedCities。
                - 询问哪些城市承担较大交通流量或城市压力排行时使用CITY_PRESSURE；城市筛选仍写入selectedCities。
                - 询问哪些路线、道路或通道日均流量大、压力大时使用ROUTE_PRESSURE；城市筛选仍写入selectedCities。
                - 询问福州或厦门综合交通运输特征、车型与时间规律时使用VEHICLE_PATTERN_OVERVIEW，并把唯一城市写入analysisCity。
                - 只询问车型构成、车型流量或车型占比时使用VEHICLE_STRUCTURE；把唯一城市写入analysisCity。
                - 只询问24小时、早晚高峰或分时出行规律时使用VEHICLE_HOURLY_PATTERN；把唯一城市写入analysisCity。
                - 只询问工作日和周末车型出行对比时使用VEHICLE_DAY_TYPE_COMPARISON；把唯一城市写入analysisCity。
                车型查询中若用户没有指定城市，或同时指定福州和厦门，analysisCity必须为null，并在clarification中追问只选择一个城市；同时提到的城市仍写入selectedCities，供Java复核。
                用户提到“通行能力”“能力利用率”“瓶颈路线”时，必须选择CAPACITY_开头的范围，不要选择普通路况范围。
                用户提到“交通联系”同时关注卡口、城市、路线压力时，优先选择REGIONAL_TRAFFIC_OVERVIEW，不要选择CITY_PAIR。
                CITY_PAIR只用于询问两个城市之间当前国省道路况、拥堵状态和路段通行情况。
                用户询问拥堵原因时仍选择最接近的交通查询类型，以便返回当前通行状态、重点路段和出行建议。
                不得把五四路、成功大道等城市道路识别为可查询路线。
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
            var scope = decision.parsedTrafficQueryType();
            if (scope.isEmpty()) {
                missing.add("trafficScope");    // 缺失范围
            } else if (scope.get() == TrafficQueryType.CITY_PAIR) {
                if (FujianCity.fromName(decision.originCity()).isEmpty()) {
                    missing.add("originCity");
                }
                if (FujianCity.fromName(decision.destinationCity()).isEmpty()) {
                    missing.add("destinationCity");
                }
            } else if ((scope.get() == TrafficQueryType.ROUTE_DETAIL
                    || scope.get() == TrafficQueryType.CAPACITY_ROUTE_DETAIL)
                    && isBlank(decision.routeCode())
                    && isBlank(decision.routeName())
                    && isBlank(decision.roadName())) {
                missing.add("routeCodeOrName");
            } else if (scope.get().regionalTrafficQuery()) {
                List<String> cities = effectiveSelectedCities(decision);
                if (cities.size() > 2 || cities.stream().anyMatch(value -> FujianCity.fromName(value).isEmpty())) {
                    missing.add("selectedCities");
                }
            } else if (scope.get().vehiclePatternQuery()) {
                List<String> selectedCities = effectiveSelectedCities(decision);
                String city = isBlank(decision.analysisCity()) ? decision.city() : decision.analysisCity();
                if (isBlank(city) && selectedCities.size() == 1) {
                    city = selectedCities.get(0);
                }
                var parsed = FujianCity.fromName(city);
                boolean conflictsWithSelection = selectedCities.size() == 1 && parsed.isPresent()
                        && FujianCity.fromName(selectedCities.get(0))
                        .map(selected -> selected != parsed.get()).orElse(true);
                if (selectedCities.size() > 1 || conflictsWithSelection || parsed.isEmpty()
                        || (parsed.get() != FujianCity.FUZHOU && parsed.get() != FujianCity.XIAMEN)) {
                    missing.add("analysisCity");
                }
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

    private List<String> effectiveSelectedCities(AgentDecision decision) {
        if (!decision.selectedCities().isEmpty()) {
            return decision.selectedCities().stream().distinct().toList();
        }
        List<String> result = new ArrayList<>();
        if (!isBlank(decision.originCity())) result.add(decision.originCity());
        if (!isBlank(decision.destinationCity())) result.add(decision.destinationCity());
        return result.stream().distinct().toList();
    }
}
