package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotPort;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final HighwayTrafficSnapshotPort trafficSnapshotPort;

    public IntentPlanner(ChatModelPort chatModelPort) {
        this(chatModelPort, null);
    }

    public IntentPlanner(ChatModelPort chatModelPort, HighwayTrafficSnapshotPort trafficSnapshotPort) {
        this.chatModelPort = chatModelPort;
        this.trafficSnapshotPort = trafficSnapshotPort;
    }

    // 方法：用户信息发送给大模型理解
    public AgentDecision plan(String currentMessage, List<ConversationMessage> history) {
        return plan(currentMessage, history, null);
    }

    public AgentDecision plan(
            String currentMessage,
            List<ConversationMessage> history,
            AgentDecision latestSuccessfulTrafficDecision
    ) {
        boolean followUp = !history.isEmpty() && currentMessage.matches("(?s).*(它|其中|这些|上述|前面|反方向|再加|加上|去掉|移除|删掉|这两个|这几个|两市|两地|第一张|第二张|只看|只展示|只保留|改为|改成|换成|那|可以|好的|同意).*");
        var inherited = followUp
                ? inheritTrafficContext(currentMessage, latestSuccessfulTrafficDecision)
                : java.util.Optional.<AgentDecision>empty();
        if (inherited.isPresent()) return inherited.get();
        var deterministic = followUp ? java.util.Optional.<AgentDecision>empty()
                : KnownTrafficQuestionClassifier.classify(currentMessage, currentRoutes());
        if (deterministic.isPresent()) {
            return deterministic.get();
        }
        String systemPrompt = """
                你是福建公路应急交通Agent的意图规划器。
                只能选择TRAFFIC_QUERY、EMERGENCY_DISPATCH、UNSUPPORTED之一。
                TRAFFIC_QUERY支持福建省普通国道、省道及交调站划分路段的交通状态、国省道通行能力、三至九市跨区域交通联系、城市目的地联系倾向，以及福州或厦门的车型出行特征分析；不支持城市道路、区县道路和高速公路。
                EMERGENCY_DISPATCH用于道路塌方、事故、水毁等事件的资源调度。
                仅提取用户明确提供或会话中已有的信息，不得编造城市、道路、位置和资源。
                必须输出json对象，字段如下：
                intent, trafficScope, originCity, destinationCity, routeCode, routeName, selectedCities, analysisCity,
                city, areaName, roadName, direction, eventType, location, severity,
                eventDescription, resourceTypes, clarification, includeTrend。
                selectedCities和resourceTypes使用字符串数组，includeTrend使用布尔值，其他不适用字段使用null。
                trafficScope只能为PROVINCE_OVERVIEW、ROUTE_CATALOG、PROVINCE_ABNORMAL、CITY_PAIR、ROUTE_DETAIL、CAPACITY_OVERVIEW、CAPACITY_BOTTLENECKS、CAPACITY_ROUTE_DETAIL、REGIONAL_TRAFFIC_OVERVIEW、REGIONAL_PAIR_PRESSURE、REGIONAL_KEY_CHANNELS、VEHICLE_PATTERN_OVERVIEW、VEHICLE_STRUCTURE、VEHICLE_HOURLY_PATTERN、VEHICLE_DAY_TYPE_COMPARISON、OD_DESTINATION_TENDENCY、OD_CONNECTION_MATRIX：
                - 询问当前覆盖路线、当前有数据的国省道或路线与路段名称对应关系时使用ROUTE_CATALOG。
                - 单独分析一个城市主要联系哪些目的地、目的地联系倾向或出行需求结构时使用OD_DESTINATION_TENDENCY，selectedCities必须恰好一个城市。
                - 分析两个及以上城市或福建九市的OD结构、城市联系矩阵时使用OD_CONNECTION_MATRIX；未限定城市时selectedCities为空并默认九市。
                - OD_*依据跨市路线代表流量形成联系倾向，不输出城市总流量不平衡、路线贡献、关键卡口或分车型OD结果，不得写成真实车辆去向、方向流量或净流入净流出。
                - OD_*支持数据库最新7日联系倾向，不支持历史月份或指定日期；这类请求仍选择最接近的OD_*，并在clarification中追问是否改查当前统计。
                - OD上下文的“再加上泉州”“去掉厦门”等追问继承此前城市范围；一个城市使用OD_DESTINATION_TENDENCY，两个及以上城市自动切换OD_CONNECTION_MATRIX。用户明确切换其他业务时不得继续套用OD。
                - “这两个城市”且上下文没有城市时必须clarification追问，不得当成全省。用户提到省外城市、区县或平潭时保留原始名称在selectedCities，不能静默丢弃，也不能映射到别的城市。
                - 询问福建省整体、全省国省道交通态势时使用PROVINCE_OVERVIEW；
                - 询问福建省哪些路段拥堵、异常或最拥堵时使用PROVINCE_ABNORMAL；
                - 询问两个福建地级市之间交通情况时使用CITY_PAIR，并分别提取originCity、destinationCity；
                - 指定G/S路线编号或国省道路线名称时使用ROUTE_DETAIL，优先提取routeCode，否则提取routeName。
                - 询问全省各国省道实际通行能力、设计通行能力或利用率总览时使用CAPACITY_OVERVIEW；
                - 询问全省哪些路线是瓶颈、严重瓶颈或通行能力利用率最高时使用CAPACITY_BOTTLENECKS；
                - 询问指定G/S路线的实际通行能力、设计通行能力、利用率或瓶颈等级时使用CAPACITY_ROUTE_DETAIL，优先提取routeCode，否则提取routeName。
                - 三至九市跨区域交通联系综合分析使用REGIONAL_TRAFFIC_OVERVIEW；只问哪些城市对压力较大使用REGIONAL_PAIR_PRESSURE；只问重要跨市路线、交通枢纽或关键卡口时使用REGIONAL_KEY_CHANNELS，并在路线层级回答，不输出卡口排名。
                - REGIONAL_*把用户明确指定的三至九个福建地级市写入selectedCities；未指定城市为空数组并默认福建九市。只指定一个或两个城市时仍选择相应REGIONAL_*，并在clarification中追问至少再补充到三个城市。
                - REGIONAL_*依据路线起终点形成无方向城市对，不表示真实OD、流向或途经城市；“再加南平”等追问必须保留此前城市后合并新增城市。
                - 询问福州或厦门综合交通运输特征、车型与时间规律时使用VEHICLE_PATTERN_OVERVIEW，并把唯一城市写入analysisCity。
                - 只询问车型构成、车型流量或车型占比时使用VEHICLE_STRUCTURE；把唯一城市写入analysisCity。
                - 只询问24小时、早晚高峰或分时出行规律时使用VEHICLE_HOURLY_PATTERN；把唯一城市写入analysisCity。
                - 只询问工作日和周末车型出行对比时使用VEHICLE_DAY_TYPE_COMPARISON；把唯一城市写入analysisCity。
                车型查询中若用户没有指定城市，analysisCity必须为null并追问城市；同时指定福州和厦门时analysisCity为null、selectedCities保留两市，由Java分别生成两份结果。
                用户提到“通行能力”“能力利用率”“瓶颈路线”时，必须选择CAPACITY_开头的范围，不要选择普通路况范围。
                用户提到跨区域或多城市“交通联系”时优先选择REGIONAL_*；CITY_PAIR只用于两个城市之间当前路况。
                CITY_PAIR只用于询问两个城市之间当前国省道路况、拥堵状态和路段通行情况。
                用户询问拥堵原因、节假日影响或重大活动影响时仍选择最接近的PROVINCE_OVERVIEW、PROVINCE_ABNORMAL、CITY_PAIR或ROUTE_DETAIL，由Java结合结构化事件事实生成原因提示。
                需求指向拥堵、异常或未来趋势时includeTrend=true；普通当前路况、容量、压力和车型查询为false。依赖“它、其中、这些路段、反方向”的追问必须继承最近一轮成功交通查询的对象和范围，不得擅自切换为全省。
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

    private java.util.Optional<AgentDecision> inheritTrafficContext(
            String message,
            AgentDecision previous
    ) {
        if (previous == null || previous.parsedIntent() != AgentIntent.TRAFFIC_QUERY
                || previous.parsedTrafficQueryType().isEmpty()) {
            return java.util.Optional.empty();
        }
        String normalized = message == null ? "" : message.replaceAll("\\s+", "");
        TrafficQueryType previousType = previous.parsedTrafficQueryType().orElse(null);
        if (previousType != null && previousType.odQuery()) {
            List<String> mentioned = mentionedCities(normalized);
            boolean changesCities = List.of("再加", "加上", "补充", "加入", "去掉", "移除", "删掉")
                    .stream().anyMatch(normalized::contains);
            boolean changesView = normalized.contains("矩阵") || normalized.contains("目的地倾向")
                    || normalized.contains("主要联系");
            if (changesCities || changesView || normalized.matches("(?s).*(这些|上述|前面|这几个).*")) {
                LinkedHashSet<String> cities = new LinkedHashSet<>(previous.selectedCities());
                if (List.of("去掉", "移除", "删掉").stream().anyMatch(normalized::contains)) cities.removeAll(mentioned);
                else cities.addAll(mentioned);
                TrafficQueryType type = cities.size() == 1
                        ? TrafficQueryType.OD_DESTINATION_TENDENCY : TrafficQueryType.OD_CONNECTION_MATRIX;
                return java.util.Optional.of(new AgentDecision(previous.intent(), type.name(), null, null,
                        null, null, List.copyOf(cities), null, previous.city(), previous.areaName(), previous.roadName(),
                        previous.direction(), previous.eventType(), previous.location(), previous.severity(),
                        previous.eventDescription(), previous.resourceTypes(), null, false));
            }
            return java.util.Optional.empty();
        }
        if (previousType != null && previousType.regionalTrafficQuery()) {
            List<String> mentioned = mentionedCities(normalized);
            boolean changesCities = List.of("再加", "加上", "补充", "加入", "去掉", "移除", "删掉")
                    .stream().anyMatch(normalized::contains);
            boolean changesDimension = List.of("城市对", "通道", "路线", "关键卡口", "交通枢纽")
                    .stream().anyMatch(normalized::contains);
            if (changesCities || changesDimension || normalized.matches("(?s).*(这些|上述|前面|这几个).*")) {
                LinkedHashSet<String> cities = new LinkedHashSet<>(previous.selectedCities());
                if (List.of("去掉", "移除", "删掉").stream().anyMatch(normalized::contains)) cities.removeAll(mentioned);
                else cities.addAll(mentioned);
                TrafficQueryType type = previousType;
                if (normalized.contains("关键卡口") || normalized.contains("交通枢纽")) type = TrafficQueryType.REGIONAL_KEY_CHANNELS;
                else if (normalized.contains("城市对")) type = TrafficQueryType.REGIONAL_PAIR_PRESSURE;
                else if (normalized.contains("通道") || normalized.contains("路线")) type = TrafficQueryType.REGIONAL_KEY_CHANNELS;
                String clarification = !cities.isEmpty() && cities.size() < 3
                        ? "区域交通联系分析至少需要三个城市，请再补充一个或多个福建地级市。" : null;
                return java.util.Optional.of(new AgentDecision(previous.intent(), type.name(), null, null,
                        null, null, List.copyOf(cities), null, previous.city(), previous.areaName(), previous.roadName(),
                        previous.direction(), previous.eventType(), previous.location(), previous.severity(),
                        previous.eventDescription(), previous.resourceTypes(), clarification, false));
            }
        }
        if (!normalized.matches("(?s).*(它|其中|这些|上述|前面|反方向|那|为什么|原因|节假日影响|活动影响).*")) {
            return java.util.Optional.empty();
        }
        String origin = previous.originCity();
        String destination = previous.destinationCity();
        if (normalized.contains("反方向")) {
            String swap = origin;
            origin = destination;
            destination = swap;
        }
        boolean includeTrend = Boolean.TRUE.equals(previous.includeTrend())
                || List.of("拥堵", "异常", "趋势", "未来", "缓解", "加剧", "持续")
                .stream().anyMatch(normalized::contains);
        return java.util.Optional.of(new AgentDecision(
                previous.intent(), previous.trafficScope(), origin, destination,
                previous.routeCode(), previous.routeName(), previous.selectedCities(), previous.analysisCity(),
                previous.city(), previous.areaName(), previous.roadName(), previous.direction(),
                previous.eventType(), previous.location(), previous.severity(), previous.eventDescription(),
                previous.resourceTypes(), null, includeTrend
        ));
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
            } else if (scope.get().odQuery()) {
                List<String> cities = effectiveSelectedCities(decision);
                if (!isBlank(decision.clarification())) missing.add("odClarification");
                boolean wrongCount = scope.get() == TrafficQueryType.OD_DESTINATION_TENDENCY
                        ? cities.size() != 1 : cities.size() == 1 || cities.size() > 9;
                if (wrongCount || cities.stream().anyMatch(value -> FujianCity.fromName(value).isEmpty())) {
                    missing.add("selectedCities");
                }
            } else if (scope.get().regionalTrafficQuery()) {
                List<String> cities = effectiveSelectedCities(decision);
                if ((!cities.isEmpty() && cities.size() < 3) || cities.size() > 9
                        || cities.stream().anyMatch(value -> FujianCity.fromName(value).isEmpty())) {
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
                boolean supportedTwoCityBatch = selectedCities.size() == 2
                        && selectedCities.stream().allMatch(value -> FujianCity.fromName(value)
                        .map(cityValue -> cityValue == FujianCity.FUZHOU || cityValue == FujianCity.XIAMEN)
                        .orElse(false));
                if (!supportedTwoCityBatch && (selectedCities.size() > 1 || conflictsWithSelection || parsed.isEmpty()
                        || (parsed.get() != FujianCity.FUZHOU && parsed.get() != FujianCity.XIAMEN))) {
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

    private List<String> mentionedCities(String text) {
        return java.util.Arrays.stream(FujianCity.values())
                .filter(city -> text.contains(city.displayName()))
                .sorted(java.util.Comparator.comparingInt(city -> text.indexOf(city.displayName())))
                .map(FujianCity::displayName).toList();
    }

    private List<cn.fj.roadagent.domain.traffic.HighwayRoute> currentRoutes() {
        if (trafficSnapshotPort == null) return List.of();
        try {
            return trafficSnapshotPort.current().routes();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
