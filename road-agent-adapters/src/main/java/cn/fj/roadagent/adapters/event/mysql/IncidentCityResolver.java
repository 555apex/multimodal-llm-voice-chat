package cn.fj.roadagent.adapters.event.mysql;

import cn.fj.roadagent.domain.traffic.FujianCity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 仅从甲方事件字段推导市级调度范围，不回写贴源表。 */
final class IncidentCityResolver {
    private static final Map<FujianCity, Point> CENTERS = Map.of(
            FujianCity.FUZHOU, new Point(26.0745, 119.2965),
            FujianCity.XIAMEN, new Point(24.4798, 118.0894),
            FujianCity.PUTIAN, new Point(25.4541, 119.0077),
            FujianCity.SANMING, new Point(26.2634, 117.6389),
            FujianCity.QUANZHOU, new Point(24.8741, 118.6757),
            FujianCity.ZHANGZHOU, new Point(24.5130, 117.6471),
            FujianCity.NANPING, new Point(26.6418, 118.1777),
            FujianCity.LONGYAN, new Point(25.0751, 117.0175),
            FujianCity.NINGDE, new Point(26.6656, 119.5482)
    );
    private static final Map<String, FujianCity> COUNTY_CITY = countyCity();

    Optional<ResolvedCity> resolve(String sourceName, String sourceOrgName, String place,
                                   String content, Double longitude, Double latitude) {
        Optional<FujianCity> city = explicitCity(sourceName)
                .or(() -> explicitCity(sourceOrgName))
                .or(() -> countyCity(place))
                .or(() -> explicitCity(content));
        if (city.isEmpty() && longitude != null && latitude != null
                && longitude >= 115 && longitude <= 121 && latitude >= 23 && latitude <= 29) {
            city = CENTERS.entrySet().stream()
                    .min(java.util.Comparator.comparingDouble(entry ->
                            squaredDistance(latitude, longitude, entry.getValue())))
                    .map(Map.Entry::getKey);
        }
        return city.map(value -> new ResolvedCity(value.adcode(), value.displayName()));
    }

    private Optional<FujianCity> explicitCity(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return java.util.Arrays.stream(FujianCity.values())
                .filter(city -> value.contains(city.displayName())).findFirst();
    }

    private Optional<FujianCity> countyCity(String place) {
        if (place == null || place.isBlank()) return Optional.empty();
        return COUNTY_CITY.entrySet().stream().filter(entry -> place.contains(entry.getKey()))
                .map(Map.Entry::getValue).findFirst();
    }

    private double squaredDistance(double lat, double lon, Point point) {
        double x = lat - point.latitude;
        double y = (lon - point.longitude) * Math.cos(Math.toRadians(lat));
        return x * x + y * y;
    }

    private static Map<String, FujianCity> countyCity() {
        Map<String, FujianCity> map = new LinkedHashMap<>();
        add(map, FujianCity.FUZHOU, "鼓楼", "台江", "仓山", "马尾", "晋安", "长乐", "闽侯", "连江", "罗源", "闽清", "永泰", "平潭", "福清");
        add(map, FujianCity.XIAMEN, "思明", "海沧", "湖里", "集美", "同安", "翔安");
        add(map, FujianCity.PUTIAN, "城厢", "涵江", "荔城", "秀屿", "仙游");
        add(map, FujianCity.SANMING, "三元", "沙县", "明溪", "清流", "宁化", "大田", "尤溪", "将乐", "泰宁", "建宁", "永安");
        add(map, FujianCity.QUANZHOU, "鲤城", "丰泽", "洛江", "泉港", "惠安", "安溪", "永春", "德化", "石狮", "晋江", "南安");
        add(map, FujianCity.ZHANGZHOU, "芗城", "龙文", "龙海", "长泰", "云霄", "漳浦", "诏安", "东山", "南靖", "平和", "华安");
        add(map, FujianCity.NANPING, "延平", "建阳", "顺昌", "浦城", "光泽", "松溪", "政和", "邵武", "武夷山", "建瓯");
        add(map, FujianCity.LONGYAN, "新罗", "永定", "长汀", "上杭", "武平", "连城", "漳平");
        add(map, FujianCity.NINGDE, "蕉城", "霞浦", "古田", "屏南", "寿宁", "周宁", "柘荣", "福安", "福鼎");
        return Map.copyOf(map);
    }

    private static void add(Map<String, FujianCity> map, FujianCity city, String... counties) {
        for (String county : counties) map.put(county, city);
    }

    record ResolvedCity(String code, String name) { }
    private record Point(double latitude, double longitude) { }
}
