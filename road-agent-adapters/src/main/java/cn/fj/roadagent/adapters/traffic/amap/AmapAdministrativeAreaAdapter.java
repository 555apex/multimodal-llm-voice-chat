package cn.fj.roadagent.adapters.traffic.amap;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.AdministrativeAreaPort;
import cn.fj.roadagent.domain.traffic.AdministrativeArea;
import cn.fj.roadagent.domain.traffic.AdministrativeAreaLevel;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.GeoBoundary;
import cn.fj.roadagent.domain.traffic.GeoPoint;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 通过高德行政区接口取得可信adcode和边界，不使用模型生成的编码。 */
public final class AmapAdministrativeAreaAdapter implements AdministrativeAreaPort {
    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Map<String, CachedArea> cache = new ConcurrentHashMap<>();

    public AmapAdministrativeAreaAdapter(
            RestClient restClient,
            String endpoint,
            String apiKey,
            Clock clock,
            Duration cacheTtl
    ) {
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public AdministrativeArea resolve(String city, String areaName) {
        String keyword = firstText(areaName, city);
        if (keyword == null) {
            throw new BusinessRuleException("TRAFFIC_AREA_REQUIRED", "请提供福建省内的城市、区或县名称");
        }
        String cacheKey = normalize(city) + "|" + normalize(keyword);
        CachedArea cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.area();
        }

        URI uri = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("key", apiKey)
                .queryParam("keywords", keyword)
                .queryParam("subdistrict", 1)
                .queryParam("extensions", "all")
                .queryParam("output", "json")
                .build().encode().toUri();
        try {
            AmapDistrictResponse response = restClient.get().uri(uri).retrieve()
                    .body(AmapDistrictResponse.class);
            AdministrativeArea area = mapResponse(city, keyword, response);
            cache.put(cacheKey, new CachedArea(area, clock.instant().plus(cacheTtl)));
            return area;
        } catch (BusinessRuleException | ExternalServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ExternalServiceException(
                    "AMAP_DISTRICT", "AMAP_DISTRICT_UPSTREAM_ERROR", "高德行政区服务调用失败", exception
            );
        }
    }

    private AdministrativeArea mapResponse(
            String requestedCity,
            String keyword,
            AmapDistrictResponse response
    ) {
        if (response == null) {
            throw new ExternalServiceException(
                    "AMAP_DISTRICT", "AMAP_DISTRICT_EMPTY_RESPONSE", "高德行政区服务返回为空"
            );
        }
        if (!"1".equals(response.status())) {
            throw new ExternalServiceException(
                    "AMAP_DISTRICT", "AMAP_DISTRICT_BUSINESS_ERROR",
                    "高德行政区服务拒绝请求：%s（%s）".formatted(response.info(), response.infocode())
            );
        }
        List<AmapDistrictResponse.District> all = new ArrayList<>();
        flatten(response.districts(), all);
        AmapDistrictResponse.District matched = all.stream()
                .filter(item -> item.adcode() != null && item.adcode().matches("\\d{6}"))
                .filter(item -> matchesName(item.name(), keyword))
                .filter(item -> isSupportedLevel(item.level()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "TRAFFIC_AREA_NOT_FOUND", "没有找到可查询的市级或区县级行政区：" + keyword
                ));
        if (!matched.adcode().startsWith("35")) {
            throw new BusinessRuleException("TRAFFIC_OUTSIDE_FUJIAN", "当前交通查询仅支持福建省内行政区");
        }

        FujianCity parentCity = cityFor(matched.adcode(), matched.level());
        if (requestedCity != null && !requestedCity.isBlank()) {
            FujianCity requested = FujianCity.fromName(requestedCity)
                    .orElseThrow(() -> new BusinessRuleException(
                            "TRAFFIC_OUTSIDE_FUJIAN", "当前交通查询仅支持福建省内城市"
                    ));
            if (requested != parentCity) {
                throw new BusinessRuleException(
                        "TRAFFIC_AREA_CITY_MISMATCH", matched.name() + "不属于" + requested.displayName()
                );
            }
        }
        GeoBoundary boundary = parseBoundary(matched.polyline());
        return new AdministrativeArea(
                matched.name(), parentCity.displayName(), matched.adcode(),
                mapLevel(matched.level(), matched.name()), boundary
        );
    }

    private FujianCity cityFor(String adcode, String level) {
        if ("city".equalsIgnoreCase(level)) {
            return FujianCity.fromAdcode(adcode)
                    .orElseThrow(() -> new BusinessRuleException(
                            "TRAFFIC_AREA_NOT_FOUND", "无法识别行政区所属福建城市"
                    ));
        }
        String cityAdcode = adcode.substring(0, 4) + "00";
        return FujianCity.fromAdcode(cityAdcode)
                .orElseThrow(() -> new BusinessRuleException(
                        "TRAFFIC_AREA_NOT_FOUND", "无法识别行政区所属福建城市"
                ));
    }

    private AdministrativeAreaLevel mapLevel(String level, String name) {
        if ("city".equalsIgnoreCase(level)) {
            return AdministrativeAreaLevel.CITY;
        }
        return name != null && (name.endsWith("县") || name.endsWith("市"))
                ? AdministrativeAreaLevel.COUNTY
                : AdministrativeAreaLevel.DISTRICT;
    }

    private GeoBoundary parseBoundary(String polyline) {
        if (polyline == null || polyline.isBlank()) {
            throw new BusinessRuleException("TRAFFIC_AREA_BOUNDARY_MISSING", "行政区没有可用边界数据");
        }
        List<List<GeoPoint>> polygons = new ArrayList<>();
        for (String polygonText : polyline.split("\\|")) {
            List<GeoPoint> points = new ArrayList<>();
            for (String pointText : polygonText.split(";")) {
                String[] parts = pointText.split(",");
                if (parts.length == 2) {
                    try {
                        points.add(new GeoPoint(
                                Double.parseDouble(parts[0]), Double.parseDouble(parts[1])
                        ));
                    } catch (NumberFormatException ignored) {
                        // 单个坏坐标不影响其余合法边界点。
                    }
                }
            }
            if (points.size() >= 3) {
                polygons.add(points);
            }
        }
        if (polygons.isEmpty()) {
            throw new BusinessRuleException("TRAFFIC_AREA_BOUNDARY_INVALID", "行政区边界数据无法解析");
        }
        return new GeoBoundary(polygons);
    }

    private void flatten(List<AmapDistrictResponse.District> source, List<AmapDistrictResponse.District> target) {
        if (source == null) {
            return;
        }
        for (AmapDistrictResponse.District item : source) {
            target.add(item);
            flatten(item.districts(), target);
        }
    }

    private boolean matchesName(String actual, String expected) {
        String left = normalize(actual);
        String right = normalize(expected);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private boolean isSupportedLevel(String level) {
        return "city".equalsIgnoreCase(level) || "district".equalsIgnoreCase(level);
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("福建省", "").replace("市", "").replace("区", "").replace("县", "");
    }

    private record CachedArea(AdministrativeArea area, Instant expiresAt) {
    }
}
