package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.VehicleTravelPatternPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.HourlyVehicleFlowResultItem;
import cn.fj.roadagent.application.traffic.TrafficSummaryResponse;
import cn.fj.roadagent.application.traffic.VehicleDayTypeResultItem;
import cn.fj.roadagent.application.traffic.VehiclePatternFacts;
import cn.fj.roadagent.application.traffic.VehicleStructureResultItem;
import cn.fj.roadagent.application.traffic.VehicleTimeFeatureResultItem;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.domain.traffic.VehicleHourlyFlow;
import cn.fj.roadagent.domain.traffic.VehicleTravelPatternSnapshot;
import cn.fj.roadagent.domain.traffic.VehicleType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 需求1-6：直接使用某城市create_time最新记录完成车型与时间特征分析。 */
public final class VehiclePatternService {
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private final VehicleTravelPatternPort dataPort;
    private final ChatModelPort chatModelPort;

    public VehiclePatternService(VehicleTravelPatternPort dataPort, ChatModelPort chatModelPort) {
        this.dataPort = dataPort;
        this.chatModelPort = chatModelPort;
    }

    public VehiclePatternFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null || !query.queryType().vehiclePatternQuery()) {
            throw new BusinessRuleException("VEHICLE_PATTERN_QUERY_TYPE_REQUIRED", "请说明车型出行特征查询类型");
        }
        FujianCity city = requireSupportedCity(resolveAnalysisCity(query));
        VehicleTravelPatternSnapshot snapshot = dataPort.latestForCity(city.displayName())
                .orElseThrow(() -> new BusinessRuleException(
                        "VEHICLE_PATTERN_NOT_FOUND", city.displayName() + "市暂无车型出行特征数据"
                ));

        List<HourlyVehicleFlowResultItem> allHours = normalizeHours(snapshot);
        List<VehicleStructureResultItem> allStructure = structure(snapshot);
        List<VehicleTimeFeatureResultItem> allTimeFeatures = timeFeatures(allHours);
        List<VehicleDayTypeResultItem> allDayTypes = dayTypes(snapshot);

        TrafficQueryType type = query.queryType();
        return new VehiclePatternFacts(
                type, title(type, city), city.displayName() + "市",
                type == TrafficQueryType.VEHICLE_PATTERN_OVERVIEW || type == TrafficQueryType.VEHICLE_STRUCTURE
                        ? allStructure : List.of(),
                type == TrafficQueryType.VEHICLE_PATTERN_OVERVIEW || type == TrafficQueryType.VEHICLE_HOURLY_PATTERN
                        ? allTimeFeatures : List.of(),
                type == TrafficQueryType.VEHICLE_PATTERN_OVERVIEW
                        || type == TrafficQueryType.VEHICLE_DAY_TYPE_COMPARISON ? allDayTypes : List.of(),
                type == TrafficQueryType.VEHICLE_PATTERN_OVERVIEW || type == TrafficQueryType.VEHICLE_HOURLY_PATTERN
                        ? allHours : List.of(),
                snapshot.acquiredAt()
        );
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        VehiclePatternFacts facts = collectFacts(query);
        TrafficSummaryResponse response = chatModelPort.generateStructuredStrict(
                summaryRequest(facts), TrafficSummaryResponse.class
        );
        String summary;
        try {
            ModelFactNumberValidator.validate(response.summary(), serializeFacts(facts));
            summary = response.summary();
        } catch (IllegalArgumentException ignored) {
            summary = deterministicSummary(facts);
        }
        String traceId = query.traceId() == null || query.traceId().isBlank()
                ? UUID.randomUUID().toString() : query.traceId();
        return HighwayTrafficResult.fromVehicleFacts(facts, summary, traceId);
    }

    private FujianCity requireSupportedCity(String input) {
        FujianCity city = FujianCity.fromName(input).orElseThrow(() ->
                new BusinessRuleException("VEHICLE_PATTERN_CITY_REQUIRED", "请选择福州市或厦门市进行车型分析"));
        if (city != FujianCity.FUZHOU && city != FujianCity.XIAMEN) {
            throw new BusinessRuleException("VEHICLE_PATTERN_CITY_UNSUPPORTED", "请选择福州市或厦门市进行车型分析");
        }
        return city;
    }

    private String resolveAnalysisCity(HighwayTrafficQuery query) {
        if (query.selectedCities().size() > 1) {
            throw new BusinessRuleException(
                    "VEHICLE_PATTERN_CITY_REQUIRED", "车型出行特征一次只能分析福州市或厦门市中的一个城市"
            );
        }
        String analysisCity = query.analysisCity();
        if ((analysisCity == null || analysisCity.isBlank()) && query.selectedCities().size() == 1) {
            return query.selectedCities().get(0);
        }
        if (analysisCity != null && !analysisCity.isBlank() && query.selectedCities().size() == 1) {
            FujianCity explicit = FujianCity.fromName(analysisCity).orElse(null);
            FujianCity selected = FujianCity.fromName(query.selectedCities().get(0)).orElse(null);
            if (explicit != null && selected != null && explicit != selected) {
                throw new BusinessRuleException(
                        "VEHICLE_PATTERN_CITY_REQUIRED", "车型分析城市必须保持唯一，请选择福州市或厦门市"
                );
            }
        }
        return analysisCity;
    }

    private List<HourlyVehicleFlowResultItem> normalizeHours(VehicleTravelPatternSnapshot snapshot) {
        Map<Integer, VehicleHourlyFlow> source = new LinkedHashMap<>();
        snapshot.hourlyFlows().stream()
                .filter(row -> row.hour().toLocalDate().equals(snapshot.dataDate()))
                .forEach(row -> {
                    VehicleHourlyFlow old = source.putIfAbsent(row.hour().getHour(), row);
                    if (old != null) {
                        throw new BusinessRuleException("VEHICLE_PATTERN_DATA_INVALID", "同一小时存在重复车型流量记录");
                    }
                });
        return java.util.stream.IntStream.range(0, 24).mapToObj(hour -> {
            VehicleHourlyFlow row = source.get(hour);
            return new HourlyVehicleFlowResultItem(
                    "%02d:00".formatted(hour),
                    row == null ? 0 : row.volume(VehicleType.CAR),
                    row == null ? 0 : row.volume(VehicleType.BUS),
                    row == null ? 0 : row.volume(VehicleType.TRUCK)
            );
        }).toList();
    }

    private List<VehicleStructureResultItem> structure(VehicleTravelPatternSnapshot snapshot) {
        long total = java.util.Arrays.stream(VehicleType.values()).mapToLong(snapshot::weeklyVolume).sum();
        return java.util.Arrays.stream(VehicleType.values()).map(type -> new VehicleStructureResultItem(
                type.name(), type.displayName(), snapshot.weeklyVolume(type),
                total == 0 ? 0d : (double) snapshot.weeklyVolume(type) / total
        )).toList();
    }

    private List<VehicleTimeFeatureResultItem> timeFeatures(List<HourlyVehicleFlowResultItem> hours) {
        return java.util.Arrays.stream(VehicleType.values()).map(type -> {
            long total = hours.stream().mapToLong(row -> volume(row, type)).sum();
            HourlyVehicleFlowResultItem peak = hours.stream()
                    .max(java.util.Comparator.<HourlyVehicleFlowResultItem>comparingLong(row -> volume(row, type))
                            .thenComparingInt(row -> -hour(row.hour())))
                    .orElseThrow();
            long peakVolume = volume(peak, type);
            long morning = hours.stream().filter(row -> hour(row.hour()) >= 7 && hour(row.hour()) <= 9)
                    .mapToLong(row -> volume(row, type)).sum();
            long evening = hours.stream().filter(row -> hour(row.hour()) >= 17 && hour(row.hour()) <= 19)
                    .mapToLong(row -> volume(row, type)).sum();
            double morningRatio = total == 0 ? 0d : (double) morning / total;
            double eveningRatio = total == 0 ? 0d : (double) evening / total;
            String peakHour = peakVolume == 0 ? "无明显峰值"
                    : peak.hour() + "–" + "%02d:59".formatted(hour(peak.hour()));
            return new VehicleTimeFeatureResultItem(
                    type.name(), type.displayName(), peakHour, peakVolume,
                    morningRatio, eveningRatio, characteristic(peakHour, total, morningRatio, eveningRatio)
            );
        }).toList();
    }

    private List<VehicleDayTypeResultItem> dayTypes(VehicleTravelPatternSnapshot snapshot) {
        return java.util.Arrays.stream(VehicleType.values()).map(type -> new VehicleDayTypeResultItem(
                type.name(), type.displayName(),
                snapshot.weeklyVolume(type) - snapshot.weekendVolume(type),
                snapshot.weekendVolume(type)
        )).toList();
    }

    private String characteristic(String peakHour, long total, double morning, double evening) {
        if (total == 0) return "当日暂无明显通行高峰";
        if (morning >= 0.25 && evening >= 0.25) return "早晚高峰均较明显，峰值位于" + peakHour;
        if (morning >= 0.40) return "通行量较集中于早高峰，峰值位于" + peakHour;
        if (evening >= 0.40) return "通行量较集中于晚高峰，峰值位于" + peakHour;
        return "全天分布相对分散，峰值位于" + peakHour;
    }

    private long volume(HourlyVehicleFlowResultItem row, VehicleType type) {
        return switch (type) {
            case CAR -> row.car();
            case BUS -> row.bus();
            case TRUCK -> row.truck();
        };
    }

    private int hour(String label) {
        return Integer.parseInt(label.substring(0, 2));
    }

    private ModelRequest summaryRequest(VehiclePatternFacts facts) {
        String prompt = """
                你是福建省交通运输车型出行特征分析助手。只能根据用户消息中的结构化事实生成摘要。
                必须输出严格JSON对象，且只能包含summary字段。summary必须是3至5句、80至600字的连贯中文。
                先概括所选城市的车型结构或时间规律，再点出占比最高车型、峰值时段或工作日周末差异中与本次查询有关的重点，最后给出简洁监测建议。
                工作日字段表示5天合计，周末字段表示2天合计，不得将二者改写成日均值；缺失小时已经由Java按0处理。
                必须直接采用结构化事实，不得重新计算、修正或补充数值，不推测事故、天气、道路原因，不输出数据异常分析，不讨论数据限制和系统实现，不使用Markdown。
                """.strip();
        return new ModelRequest(prompt, serializeFacts(facts), List.of(), 0.1);
    }

    private String serializeFacts(VehiclePatternFacts facts) {
        StringBuilder out = new StringBuilder();
        out.append("queryType=").append(facts.queryType()).append('\n');
        out.append("title=").append(facts.title()).append('\n');
        out.append("analysisCity=").append(facts.analysisCity()).append('\n');
        out.append("acquiredAt=").append(facts.acquiredAt()).append('\n');
        out.append("calculationPolicy=车型3类；24小时；工作日5天合计；周末2天合计；早高峰07至09；晚高峰17至19\n");
        out.append("vehicleStructureRows:\n");
        facts.structureRows().forEach(row -> out.append("- ").append(row.vehicleTypeName())
                .append("|周通行量=").append(row.weeklyVolume())
                .append("|占比=").append(format(row.shareRatio() * 100)).append("%\n"));
        out.append("vehicleTimeFeatureRows:\n");
        facts.timeFeatureRows().forEach(row -> out.append("- ").append(row.vehicleTypeName())
                .append("|最高峰=").append(row.peakHour()).append('|').append(row.peakVolume())
                .append("|早高峰占比=").append(format(row.morningPeakRatio() * 100)).append('%')
                .append("|晚高峰占比=").append(format(row.eveningPeakRatio() * 100)).append('%')
                .append("|特征=").append(row.characteristic()).append('\n'));
        out.append("vehicleDayTypeRows:\n");
        facts.dayTypeRows().forEach(row -> out.append("- ").append(row.vehicleTypeName())
                .append("|工作日5天合计=").append(row.weekdayVolume())
                .append("|周末2天合计=").append(row.weekendVolume()).append('\n'));
        return out.toString();
    }

    private String deterministicSummary(VehiclePatternFacts facts) {
        String first = "已完成" + facts.analysisCity() + "当前车型出行特征分析，相关统计按该城市最新记录形成。";
        String second;
        if (!facts.structureRows().isEmpty()) {
            VehicleStructureResultItem top = facts.structureRows().stream()
                    .max(java.util.Comparator.comparingLong(VehicleStructureResultItem::weeklyVolume))
                    .orElseThrow();
            second = top.vehicleTypeName() + "是当前周通行量占比最高的车型，可作为运输结构监测重点。";
        } else if (!facts.timeFeatureRows().isEmpty()) {
            VehicleTimeFeatureResultItem top = facts.timeFeatureRows().stream()
                    .max(java.util.Comparator.comparingLong(VehicleTimeFeatureResultItem::peakVolume))
                    .orElseThrow();
            second = top.vehicleTypeName() + "的主要峰值时段为" + top.peakHour()
                    + "，可结合折线图观察全天出行节奏。";
        } else if (!facts.dayTypeRows().isEmpty()) {
            second = "工作日与周末的分车型通行量已完成对比，可结合柱状图关注不同车型的出行差异。";
        } else {
            second = "当前车型统计结果已完成整理，可结合下方明细查看各项运输特征。";
        }
        return first + second + "建议持续跟踪后续批次变化，为重点时段交通组织提供参考。";
    }

    private String title(TrafficQueryType type, FujianCity city) {
        String prefix = city.displayName() + "市";
        return switch (type) {
            case VEHICLE_STRUCTURE -> prefix + "车型结构分析";
            case VEHICLE_HOURLY_PATTERN -> prefix + "24小时分车型出行规律";
            case VEHICLE_DAY_TYPE_COMPARISON -> prefix + "工作日与周末出行对比";
            default -> prefix + "交通运输特征分析";
        };
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
