package cn.fj.roadagent.adapters.traffic.amap;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.AreaTrafficDataPort;
import cn.fj.roadagent.application.traffic.AreaTrafficProgress;
import cn.fj.roadagent.application.traffic.AreaTrafficProgressListener;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.GeoPoint;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 将行政区边界切成高德允许的矩形，并发查询后合并为一个标准快照。 */
public final class AmapAreaTrafficDataAdapter implements AreaTrafficDataPort, AutoCloseable {
    private static final Set<String> SUPPORTED_FUJIAN_ADCODES = Set.of("350100", "350200", "350500");

    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final Clock clock;
    private final double tileSizeKm;
    private final int maxTiles;
    private final Duration cacheTtl;
    private final ExecutorService executor;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    public AmapAreaTrafficDataAdapter(
            RestClient restClient,
            String endpoint,
            String apiKey,
            Clock clock,
            double tileSizeKm,
            int maxTiles,
            int concurrency,
            Duration cacheTtl
    ) {
        if (tileSizeKm <= 0 || tileSizeKm > 7.0) {
            throw new IllegalArgumentException("区域切片边长必须在0至7公里之间");
        }
        if (maxTiles < 1 || concurrency < 1) {
            throw new IllegalArgumentException("切片上限和并发数必须大于0");
        }
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.clock = clock;
        this.tileSizeKm = tileSizeKm;
        this.maxTiles = maxTiles;
        this.cacheTtl = cacheTtl;
        this.executor = Executors.newFixedThreadPool(concurrency);
    }

    @Override
    public AreaTrafficSnapshot query(AreaTrafficQuery query, AreaTrafficProgressListener listener) {
        requireSupportedCity(query);
        String cacheKey = query.area().adcode() + "|" + query.scope();
        CachedSnapshot cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            var coverage = cached.snapshot().coverage();
            listener.onProgress(new AreaTrafficProgress(
                    coverage.totalTiles(), coverage.totalTiles(), coverage.failedTiles()
            ));
            return cached.snapshot();
        }

        Geometry areaGeometry = toGeometry(query);
        List<AreaTile> tiles = buildTiles(areaGeometry);
        if (tiles.isEmpty()) {
            throw new BusinessRuleException("TRAFFIC_AREA_BOUNDARY_INVALID", "行政区边界无法生成查询切片");
        }
        if (tiles.size() > maxTiles) {
            throw new BusinessRuleException(
                    "AREA_QUERY_TOO_LARGE",
                    "行政区需要%d个切片，超过当前上限%d，请缩小查询范围".formatted(tiles.size(), maxTiles)
            );
        }

        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        listener.onProgress(new AreaTrafficProgress(tiles.size(), 0, 0));
        List<CompletableFuture<TileResult>> futures = tiles.stream()
                .map(tile -> CompletableFuture.supplyAsync(() -> {
                    TileResult result;
                    try {
                        result = queryTile(query, tile, areaGeometry);
                    } catch (RuntimeException exception) {
                        failed.incrementAndGet();
                        result = TileResult.failed();
                    }
                    int done = completed.incrementAndGet();
                    listener.onProgress(new AreaTrafficProgress(tiles.size(), done, failed.get()));
                    return result;
                }, executor))
                .toList();
        List<TileResult> results = futures.stream().map(CompletableFuture::join).toList();
        int failedTiles = failed.get();
        int succeededTiles = tiles.size() - failedTiles;
        if (succeededTiles == 0) {
            throw new ExternalServiceException(
                    "AMAP", "AMAP_AREA_ALL_TILES_FAILED", "高德区域交通查询的全部切片均失败"
            );
        }

        List<RoadSegmentStatus> segments = mergeSegments(results);
        TrafficCoverage coverage = TrafficCoverage.of(tiles.size(), succeededTiles, failedTiles);
        List<String> warnings = new ArrayList<>();
        if (!coverage.complete()) {
            warnings.add("PARTIAL_AREA_COVERAGE");
        }
        if (segments.isEmpty()) {
            warnings.add("EMPTY_TRAFFIC_DATA");
        }
        String description = results.stream()
                .map(TileResult::description)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
        AreaTrafficSnapshot snapshot = new AreaTrafficSnapshot(
                query, segments, TrafficEvaluation.from(segments), coverage, "AMAP",
                clock.instant(), description, warnings
        );
        cache.put(cacheKey, new CachedSnapshot(snapshot, clock.instant().plus(cacheTtl)));
        return snapshot;
    }

    private void requireSupportedCity(AreaTrafficQuery query) {
        FujianCity city = FujianCity.fromName(query.area().city())
                .orElseThrow(() -> new BusinessRuleException(
                        "TRAFFIC_OUTSIDE_FUJIAN", "当前交通查询仅支持福建省内城市"
                ));
        if (!SUPPORTED_FUJIAN_ADCODES.contains(city.adcode())) {
            throw new ExternalServiceException(
                    "AMAP", "TRAFFIC_AREA_UNSUPPORTED_BY_PROVIDER",
                    "当前高德交通态势数据源暂不覆盖" + city.displayName()
            );
        }
    }

    private Geometry toGeometry(AreaTrafficQuery query) {
        List<Geometry> polygons = query.area().boundary().polygons().stream()
                .map(points -> (Geometry) toPolygon(points))
                .filter(geometry -> !geometry.isEmpty())
                .toList();
        Geometry geometry = UnaryUnionOp.union(polygons);
        if (!geometry.isValid()) {
            geometry = geometry.buffer(0);
        }
        return geometry;
    }

    private Polygon toPolygon(List<GeoPoint> points) {
        List<Coordinate> coordinates = points.stream()
                .map(point -> new Coordinate(point.longitude(), point.latitude()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Coordinate first = coordinates.get(0);
        Coordinate last = coordinates.get(coordinates.size() - 1);
        if (!first.equals2D(last)) {
            coordinates.add(new Coordinate(first));
        }
        LinearRing shell = geometryFactory.createLinearRing(coordinates.toArray(Coordinate[]::new));
        return geometryFactory.createPolygon(shell);
    }

    private List<AreaTile> buildTiles(Geometry areaGeometry) {
        Envelope envelope = areaGeometry.getEnvelopeInternal();
        double middleLatitude = (envelope.getMinY() + envelope.getMaxY()) / 2.0;
        double latitudeStep = tileSizeKm / 111.32;
        double longitudeStep = tileSizeKm / (111.32 * Math.cos(Math.toRadians(middleLatitude)));
        List<AreaTile> tiles = new ArrayList<>();
        for (double minY = envelope.getMinY(); minY < envelope.getMaxY(); minY += latitudeStep) {
            double maxY = Math.min(minY + latitudeStep, envelope.getMaxY());
            for (double minX = envelope.getMinX(); minX < envelope.getMaxX(); minX += longitudeStep) {
                double maxX = Math.min(minX + longitudeStep, envelope.getMaxX());
                Envelope tileEnvelope = new Envelope(minX, maxX, minY, maxY);
                Geometry tileGeometry = geometryFactory.toGeometry(tileEnvelope);
                if (areaGeometry.intersects(tileGeometry)) {
                    tiles.add(new AreaTile(minX, minY, maxX, maxY));
                }
            }
        }
        return tiles;
    }

    private TileResult queryTile(AreaTrafficQuery query, AreaTile tile, Geometry areaGeometry) {
        URI uri = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("key", apiKey)
                .queryParam("level", query.roadLevel())
                .queryParam("rectangle", tile.asParameter())
                .queryParam("extensions", "all")
                .queryParam("output", "json")
                .build().encode().toUri();
        try {
            AmapTrafficResponse response = restClient.get().uri(uri).retrieve()
                    .body(AmapTrafficResponse.class);
            if (response == null) {
                throw new ExternalServiceException("AMAP", "AMAP_EMPTY_RESPONSE", "高德区域交通服务返回为空");
            }
            if (!"1".equals(response.status())) {
                throw new ExternalServiceException(
                        "AMAP", "AMAP_AREA_BUSINESS_ERROR",
                        "高德区域交通服务拒绝请求：%s（%s）".formatted(response.info(), response.infocode())
                );
            }
            AmapTrafficResponse.TrafficInfo info = response.trafficInfo();
            List<RoadSegmentStatus> segments = info == null || info.roads() == null
                    ? List.of()
                    : info.roads().stream().map(this::mapRoad)
                    .filter(segment -> intersectsArea(segment, areaGeometry))
                    .toList();
            return new TileResult(segments, info == null ? "" : info.description());
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ExternalServiceException(
                    "AMAP", "AMAP_AREA_UPSTREAM_ERROR", "高德区域交通服务调用失败", exception
            );
        }
    }

    private RoadSegmentStatus mapRoad(AmapTrafficResponse.Road road) {
        return new RoadSegmentStatus(
                road.name(), road.direction(), mapStatus(road.status()), parseSpeed(road.speed()), road.polyline()
        );
    }

    private boolean intersectsArea(RoadSegmentStatus segment, Geometry areaGeometry) {
        if (segment.polyline() == null || segment.polyline().isBlank()) {
            return true;
        }
        try {
            Coordinate[] coordinates = java.util.Arrays.stream(segment.polyline().split(";"))
                    .map(point -> point.split(","))
                    .filter(parts -> parts.length == 2)
                    .map(parts -> new Coordinate(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])))
                    .toArray(Coordinate[]::new);
            return coordinates.length < 2
                    || areaGeometry.intersects(geometryFactory.createLineString(coordinates));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private List<RoadSegmentStatus> mergeSegments(List<TileResult> results) {
        Map<String, RoadSegmentStatus> merged = new LinkedHashMap<>();
        results.stream().flatMap(result -> result.segments().stream()).forEach(segment ->
                merged.merge(segmentKey(segment), segment, this::mergeDuplicate)
        );
        return merged.values().stream()
                .sorted(Comparator.comparingInt(
                        (RoadSegmentStatus item) -> item.congestionLevel().severity()).reversed()
                        .thenComparing(RoadSegmentStatus::roadName)
                        .thenComparing(RoadSegmentStatus::direction))
                .toList();
    }

    private RoadSegmentStatus mergeDuplicate(RoadSegmentStatus left, RoadSegmentStatus right) {
        CongestionLevel level = left.congestionLevel().severity() >= right.congestionLevel().severity()
                ? left.congestionLevel() : right.congestionLevel();
        Double speed = minimumSpeed(left.averageSpeedKmh(), right.averageSpeedKmh());
        return new RoadSegmentStatus(left.roadName(), left.direction(), level, speed, left.polyline());
    }

    private String segmentKey(RoadSegmentStatus segment) {
        return segment.roadName().trim().toLowerCase() + "|"
                + segment.direction().trim().toLowerCase() + "|"
                + (segment.polyline() == null ? "" : segment.polyline());
    }

    private Double minimumSpeed(Double left, Double right) {
        if (left == null) return right;
        if (right == null) return left;
        return Math.min(left, right);
    }

    private CongestionLevel mapStatus(String status) {
        return switch (status == null ? "" : status) {
            case "1" -> CongestionLevel.SMOOTH;
            case "2" -> CongestionLevel.SLOW;
            case "3" -> CongestionLevel.CONGESTED;
            default -> CongestionLevel.UNKNOWN;
        };
    }

    private Double parseSpeed(String speed) {
        if (speed == null || speed.isBlank()) return null;
        try {
            return Double.valueOf(speed);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    private record AreaTile(double minX, double minY, double maxX, double maxY) {
        String asParameter() {
            return "%.6f,%.6f;%.6f,%.6f".formatted(minX, minY, maxX, maxY);
        }
    }

    private record TileResult(List<RoadSegmentStatus> segments, String description) {
        static TileResult failed() {
            return new TileResult(List.of(), "");
        }
    }

    private record CachedSnapshot(AreaTrafficSnapshot snapshot, Instant expiresAt) {
    }
}
