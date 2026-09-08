package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.dispatch.ClassifyEmergencyEventsUseCase;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.EventClassificationLogPort;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.domain.dispatch.EmergencyEventType;
import cn.fj.roadagent.domain.dispatch.EventClassificationAttempt;
import cn.fj.roadagent.domain.dispatch.EventClassificationMethod;
import cn.fj.roadagent.domain.dispatch.EventClassificationStatus;
import cn.fj.roadagent.domain.dispatch.UnclassifiedEmergencyEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 规则优先、模型兜底的应急事件自动分类器。 */
public final class EmergencyEventClassificationService implements ClassifyEmergencyEventsUseCase {
    private static final Map<EmergencyEventType, List<String>> KEYWORDS = keywords();
    private final AbnormalEventPort eventPort;
    private final EventClassificationLogPort logPort;
    private final ChatModelPort modelPort;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final Duration retryBackoff;
    private final String modelName;

    public EmergencyEventClassificationService(
            AbnormalEventPort eventPort,
            EventClassificationLogPort logPort,
            ChatModelPort modelPort,
            UnitOfWork unitOfWork,
            Clock clock,
            Duration retryBackoff,
            String modelName
    ) {
        this.eventPort = eventPort;
        this.logPort = logPort;
        this.modelPort = modelPort;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.retryBackoff = retryBackoff;
        this.modelName = modelName;
    }

    @Override
    public boolean classifyNext() {
        return eventPort.findNextUnclassified(clock.instant().minus(retryBackoff))
                .map(event -> {
                    classify(event);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean retryNext() {
        return eventPort.findNextUnclassified(clock.instant())
                .map(event -> {
                    classify(event);
                    return true;
                }).orElse(false);
    }

    @Override
    public void retry(String eventId) {
        UnclassifiedEmergencyEvent event = eventPort.findUnclassifiedById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("待分类应急事件不存在"));
        classify(event);
    }

    private void classify(UnclassifiedEmergencyEvent event) {
        Classification classification;
        try {
            classification = rule(event).orElseGet(() -> model(event));
            EmergencyEventType type = EmergencyEventType.require(classification.type());
            Instant now = clock.instant();
            unitOfWork.required(() -> {
                int attempt = logPort.nextAttemptNumber(event.eventId());
                EventClassificationAttempt record = new EventClassificationAttempt(
                        id(), event.eventId(), attempt, classification.method(),
                        EventClassificationStatus.SUCCEEDED, type.name(), classification.confidence(),
                        classification.evidence(), classification.method() == EventClassificationMethod.MODEL
                                ? modelName : null,
                        null, "auto-classify-" + event.eventId() + "-" + attempt, now
                );
                if (!logPort.insert(record)) throw new IllegalStateException("分类留痕已存在");
                if (!eventPort.assignEventTypeIfAbsent(event.eventId(), type.name())) {
                    throw new AlreadyClassifiedException();
                }
            });
        } catch (AlreadyClassifiedException ignored) {
            // 另一轮分类已经先完成条件更新；当前事务回滚，不额外记录失败。
        } catch (RuntimeException exception) {
            recordFailure(event, exception);
            throw exception;
        }
    }

    private java.util.Optional<Classification> rule(UnclassifiedEmergencyEvent event) {
        String text = String.join(" ", nonNull(event.description()), nonNull(event.place()),
                nonNull(event.routeName()));
        Map<EmergencyEventType, Integer> scores = new LinkedHashMap<>();
        KEYWORDS.forEach((type, words) -> {
            int score = words.stream().filter(text::contains).mapToInt(String::length).sum();
            if (score > 0) scores.put(type, score);
        });
        if (scores.isEmpty()) return java.util.Optional.empty();
        int max = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<EmergencyEventType> winners = scores.entrySet().stream()
                .filter(entry -> entry.getValue() == max).map(Map.Entry::getKey).toList();
        if (winners.size() != 1) return java.util.Optional.empty();
        EmergencyEventType winner = winners.get(0);
        String evidence = KEYWORDS.get(winner).stream().filter(text::contains).toList().toString();
        return java.util.Optional.of(new Classification(
                winner.name(), 0.95, "命中受控关键词" + evidence, EventClassificationMethod.RULE));
    }

    private Classification model(UnclassifiedEmergencyEvent event) {
        String allowed = java.util.Arrays.stream(EmergencyEventType.values())
                .map(type -> type.name() + "=" + type.displayName())
                .collect(java.util.stream.Collectors.joining("、"));
        EventClassificationProposal proposal = modelPort.generateStructuredStrict(new ModelRequest(
                """
                你是福建公路应急事件分类器。只进行分类，不生成处置方案。
                必须返回严格JSON对象，仅包含eventType、confidence、evidence。
                eventType必须从用户给定的16个代码中单选；confidence为0到1的数字；evidence为简短中文依据。
                不得返回候选列表、Markdown或额外字段。
                """,
                """
                可选类型：%s
                事件描述：%s
                发生地点：%s
                路线：%s %s
                请选择最可能的唯一类型，即使置信度较低也必须返回最高概率结果。
                """.formatted(allowed, event.description(), nonNull(event.place()),
                        nonNull(event.routeNo()), nonNull(event.routeName())),
                List.of(), 0.0), EventClassificationProposal.class);
        if (proposal == null || proposal.evidence() == null || proposal.evidence().isBlank()) {
            throw new IllegalArgumentException("模型分类结果不完整");
        }
        EmergencyEventType type = EmergencyEventType.require(proposal.eventType());
        if (Double.isNaN(proposal.confidence()) || proposal.confidence() < 0 || proposal.confidence() > 1) {
            throw new IllegalArgumentException("模型置信度必须在0到1之间");
        }
        return new Classification(type.name(), proposal.confidence(), proposal.evidence(),
                EventClassificationMethod.MODEL);
    }

    private void recordFailure(UnclassifiedEmergencyEvent event, RuntimeException exception) {
        try {
            unitOfWork.required(() -> {
                int attempt = logPort.nextAttemptNumber(event.eventId());
                logPort.insert(new EventClassificationAttempt(
                        id(), event.eventId(), attempt, EventClassificationMethod.MODEL,
                        EventClassificationStatus.FAILED, null, 0, null, modelName,
                        safeMessage(exception), "auto-classify-failed-" + event.eventId() + "-" + attempt,
                        clock.instant()));
            });
        } catch (RuntimeException ignored) {
            // 原始错误优先返回；并发重复留痕不覆盖其它尝试。
        }
    }

    private String safeMessage(Throwable throwable) {
        String value = throwable.getMessage();
        if (value == null || value.isBlank()) value = throwable.getClass().getSimpleName();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String id() {
        return "CL-" + UUID.randomUUID();
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static Map<EmergencyEventType, List<String>> keywords() {
        Map<EmergencyEventType, List<String>> map = new LinkedHashMap<>();
        map.put(EmergencyEventType.DT01, List.of("崩塌", "落石", "滚石", "溜方", "塌方"));
        map.put(EmergencyEventType.DT02, List.of("滑坡", "坡体位移", "山体滑移"));
        map.put(EmergencyEventType.DT03, List.of("泥石流"));
        map.put(EmergencyEventType.DT04, List.of("路面塌陷", "路基沉陷", "地面塌陷", "沉陷"));
        map.put(EmergencyEventType.DT05, List.of("水毁", "冲毁", "路面积水", "内涝", "洪水"));
        map.put(EmergencyEventType.ET101, List.of("拥堵", "车辆滞留", "缓行"));
        map.put(EmergencyEventType.ET102, List.of("明火", "起火", "火灾", "燃烧"));
        map.put(EmergencyEventType.ET103, List.of("抛撒物", "遗撒", "散落物", "货物撒落"));
        map.put(EmergencyEventType.ET104, List.of("设备故障", "机电故障", "信号灯故障", "收费设备故障"));
        map.put(EmergencyEventType.ET105, List.of("占用应急车道", "应急车道被占"));
        map.put(EmergencyEventType.ET106, List.of("交通事故", "相撞", "追尾", "碰撞", "侧翻"));
        map.put(EmergencyEventType.ET107, List.of("异常停车", "车辆停在", "故障车停靠"));
        map.put(EmergencyEventType.ET108, List.of("浓雾", "团雾", "能见度低"));
        map.put(EmergencyEventType.ET109, List.of("路障", "障碍物", "道路阻断物"));
        map.put(EmergencyEventType.ET110, List.of("道路施工", "应急施工", "抢修施工"));
        map.put(EmergencyEventType.ET112, List.of("道路积雪", "路面积雪", "路面结冰", "冰雪"));
        return Map.copyOf(map);
    }

    private record Classification(String type, double confidence, String evidence,
                                  EventClassificationMethod method) { }

    private static final class AlreadyClassifiedException extends RuntimeException { }
}
