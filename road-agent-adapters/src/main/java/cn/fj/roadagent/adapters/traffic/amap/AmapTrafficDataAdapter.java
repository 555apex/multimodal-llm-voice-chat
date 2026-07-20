package cn.fj.roadagent.adapters.traffic.amap;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.util.List;

public final class AmapTrafficDataAdapter implements TrafficDataPort {
// 高德数据源数据实现：(adapters层)的AmapTrafficDataAdapter 实现 (application层)提供的数据接口：TrafficDataPort

    private final RestClient restClient;    // Spring的HTTP客户端
    private final String endpoint;  // 高德API地址
    private final String apiKey;    // 高德API密钥
    private final int roadLevel;    // 道路等级（高速/国道/省道...）
    private final Clock clock;      // 时钟

    // 构造函数
    public AmapTrafficDataAdapter(
            RestClient restClient,
            String endpoint,
            String apiKey,
            int roadLevel,
            Clock clock
    ) {
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.roadLevel = roadLevel;
        this.clock = clock;
    }

    @Override
    // 实现：构造url -> 发请求拿结果
    public TrafficSnapshot query(TrafficQuery query) {
        URI uri = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("key", apiKey)      // 高德API密钥
                .queryParam("level", roadLevel)     // 道路等级
                .queryParam("name", query.roadName())       // 道路名称
                .queryParam("adcode", query.areaCode())     // 行政区划编码
                .queryParam("extensions", "all")    // 返回全部字段
                .queryParam("output", "json")   // 返回输出格式：JSON
                .build()
                .encode()
                .toUri();

        try {   // 发HTTP请求
            AmapTrafficResponse response = restClient.get() // 高德API是查询操作，用get方法
                    .uri(uri)
                    .retrieve()
                    .body(AmapTrafficResponse.class);   // 执行请求，获取响应，并将响应体JSON转化为java对象AmapTrafficResponse
            return mapResponse(query, response);    // 把高德格式转化为domain对象(TrafficSnapshot)
        } catch (ExternalServiceException exception) {
            throw exception;    // 自定义异常，上抛
        } catch (RestClientException exception) {
            throw new ExternalServiceException(     // HTTP异常(超时、连接失败)，包装为自定义的异常ExternalServiceException
                    "AMAP", "AMAP_UPSTREAM_ERROR", "高德交通服务调用失败", exception
            );
        }
    }

    // 方法：高德数据格式转化为domain定义格式
    private TrafficSnapshot mapResponse(TrafficQuery query, AmapTrafficResponse response) {
        if (response == null) { // 服务错误检查
            throw new ExternalServiceException("AMAP", "AMAP_EMPTY_RESPONSE", "高德交通服务返回为空");
        }
        if (!"1".equals(response.status())) {   // 检查高德API是否返回成功
            String code = response.infocode() != null && response.infocode().startsWith("100")
                    ? "AMAP_AUTH_OR_PERMISSION_ERROR"
                    : "AMAP_BUSINESS_ERROR";
            String message = "高德交通服务拒绝请求：%s（%s）"
                    .formatted(response.info(), response.infocode());
            throw new ExternalServiceException("AMAP", code, message);
        }

        AmapTrafficResponse.TrafficInfo trafficInfo = response.trafficInfo();
        List<RoadSegmentStatus> segments = trafficInfo == null || trafficInfo.roads() == null
                ? List.of()     // 没数据，返回空列表
                : trafficInfo.roads().stream()  // 有数据，逐条转换
                .map(this::mapRoad)     // 把每条 Road 转成 RoadSegmentStatus（domain定义的对象格式）
                .toList(); // 收集为List
        String description = trafficInfo == null ? "" : trafficInfo.description();

        return new TrafficSnapshot(query, segments, "AMAP", clock.instant(), false, description);
    }

    // 方法：高德返回的road转化，返回RoadSegmentStatus（domain层定义）
    private RoadSegmentStatus mapRoad(AmapTrafficResponse.Road road) {
        return new RoadSegmentStatus(
                road.name(),
                road.direction(),
                mapStatus(road.status()),
                parseSpeed(road.speed()),
                road.polyline()
        );
    }

    // 方法：状态映射，高德返回状态转化，返回CongestionLevel（domain层定义）
    private CongestionLevel mapStatus(String status) {
        return switch (status == null ? "" : status) {
            case "1" -> CongestionLevel.SMOOTH;
            case "2" -> CongestionLevel.SLOW;
            case "3" -> CongestionLevel.CONGESTED;
            default -> CongestionLevel.UNKNOWN;
        };
    }

    // 方法：速度解析
    private Double parseSpeed(String speed) {
        if (speed == null || speed.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(speed);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
