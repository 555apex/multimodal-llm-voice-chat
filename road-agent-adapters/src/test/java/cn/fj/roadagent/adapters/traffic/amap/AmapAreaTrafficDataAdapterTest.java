package cn.fj.roadagent.adapters.traffic.amap;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.traffic.AreaTrafficProgress;
import cn.fj.roadagent.domain.traffic.AdministrativeArea;
import cn.fj.roadagent.domain.traffic.AdministrativeAreaLevel;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.GeoBoundary;
import cn.fj.roadagent.domain.traffic.GeoPoint;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapAreaTrafficDataAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), ZoneOffset.UTC);
    private MockRestServiceServer server;
    private AmapAreaTrafficDataAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AmapAreaTrafficDataAdapter(
                builder.build(), "https://restapi.amap.com/v3/traffic/status/rectangle", "test-key",
                CLOCK, 6.0, 500, 1, Duration.ofMinutes(2)
        );
    }

    @AfterEach
    void tearDown() {
        adapter.close();
    }

    @Test
    void shouldUseLevelFourForMajorRoadsAndReuseCache() {
        server.expect(once(), requestTo(containsString("level=4")))
                .andRespond(withSuccess(successResponse("成功大道", "3", "12"), MediaType.APPLICATION_JSON));
        List<AreaTrafficProgress> progress = new ArrayList<>();
        AreaTrafficQuery query = new AreaTrafficQuery(smallSimingArea(), TrafficQueryScope.AREA_MAJOR);

        var first = adapter.query(query, progress::add);
        var second = adapter.query(query, progress::add);

        assertEquals(CongestionLevel.CONGESTED, first.segments().get(0).congestionLevel());
        assertEquals(12.0, first.evaluation().averageSpeedKmh());
        assertTrue(first.coverage().complete());
        assertEquals(first, second);
        assertTrue(progress.stream().anyMatch(item -> item.completedTiles() == item.totalTiles()));
        server.verify();
    }

    @Test
    void shouldReturnPartialCoverageWhenOneTileFails() {
        adapter.close();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AmapAreaTrafficDataAdapter(
                builder.build(), "https://restapi.amap.com/v3/traffic/status/rectangle", "test-key",
                CLOCK, 6.0, 500, 1, Duration.ofMinutes(2)
        );
        server.expect(requestTo(containsString("level=5")))
                .andRespond(withSuccess(successResponse("厦禾路", "1", "35"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("level=5"))).andRespond(withServerError());

        var snapshot = adapter.query(
                new AreaTrafficQuery(wideSimingArea(), TrafficQueryScope.AREA_ALL), ignored -> { }
        );

        assertEquals(2, snapshot.coverage().totalTiles());
        assertEquals(1, snapshot.coverage().failedTiles());
        assertEquals(0.5, snapshot.coverage().coverageRatio());
        assertTrue(snapshot.warnings().contains("PARTIAL_AREA_COVERAGE"));
        server.verify();
    }

    @Test
    void shouldFailWhenEveryTileFails() {
        server.expect(requestTo(containsString("rectangle="))).andRespond(withServerError());

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, () -> adapter.query(
                new AreaTrafficQuery(smallSimingArea(), TrafficQueryScope.AREA_ALL), ignored -> { }
        ));

        assertEquals("AMAP_AREA_ALL_TILES_FAILED", exception.errorCode());
        server.verify();
    }

    @Test
    void shouldDeduplicateSameRoadAndKeepMoreSevereSlowerResult() {
        server.expect(requestTo(containsString("level=5")))
                .andRespond(withSuccess("""
                        {
                          "status":"1","info":"OK","infocode":"10000",
                          "trafficinfo":{"description":"区域路况","roads":[
                            {"name":"成功大道","status":"1","direction":"北向南","speed":"35",
                             "polyline":"118.085,24.445;118.095,24.455"},
                            {"name":"成功大道","status":"3","direction":"北向南","speed":"12",
                             "polyline":"118.085,24.445;118.095,24.455"}
                          ]}
                        }
                        """, MediaType.APPLICATION_JSON));

        var snapshot = adapter.query(
                new AreaTrafficQuery(smallSimingArea(), TrafficQueryScope.AREA_ALL), ignored -> { }
        );

        assertEquals(1, snapshot.segments().size());
        assertEquals(CongestionLevel.CONGESTED, snapshot.segments().get(0).congestionLevel());
        assertEquals(12.0, snapshot.segments().get(0).averageSpeedKmh());
        server.verify();
    }

    @Test
    void shouldRejectAreaThatExceedsConfiguredTileLimit() {
        adapter.close();
        RestClient.Builder builder = RestClient.builder();
        adapter = new AmapAreaTrafficDataAdapter(
                builder.build(), "https://restapi.amap.com/v3/traffic/status/rectangle", "test-key",
                CLOCK, 6.0, 1, 1, Duration.ofMinutes(2)
        );

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> adapter.query(
                new AreaTrafficQuery(wideSimingArea(), TrafficQueryScope.AREA_ALL), ignored -> { }
        ));

        assertEquals("AREA_QUERY_TOO_LARGE", exception.errorCode());
    }

    @Test
    void shouldFailWhenProviderDoesNotCoverFujianCity() {
        AdministrativeArea ningde = new AdministrativeArea(
                "宁德市", "宁德", "350900", AdministrativeAreaLevel.CITY,
                new GeoBoundary(List.of(List.of(
                        new GeoPoint(119.40, 26.60), new GeoPoint(119.42, 26.60),
                        new GeoPoint(119.42, 26.62), new GeoPoint(119.40, 26.62)
                )))
        );

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, () -> adapter.query(
                new AreaTrafficQuery(ningde, TrafficQueryScope.AREA_ALL), ignored -> { }
        ));
        assertEquals("TRAFFIC_AREA_UNSUPPORTED_BY_PROVIDER", exception.errorCode());
    }

    private AdministrativeArea smallSimingArea() {
        return area(118.080, 118.110);
    }

    private AdministrativeArea wideSimingArea() {
        return area(118.080, 118.180);
    }

    private AdministrativeArea area(double minLongitude, double maxLongitude) {
        return new AdministrativeArea(
                "思明区", "厦门", "350203", AdministrativeAreaLevel.DISTRICT,
                new GeoBoundary(List.of(List.of(
                        new GeoPoint(minLongitude, 24.440), new GeoPoint(maxLongitude, 24.440),
                        new GeoPoint(maxLongitude, 24.460), new GeoPoint(minLongitude, 24.460)
                )))
        );
    }

    private String successResponse(String road, String status, String speed) {
        return """
                {
                  "status":"1","info":"OK","infocode":"10000",
                  "trafficinfo":{"description":"区域路况", "roads":[{
                    "name":"%s","status":"%s","direction":"北向南","speed":"%s",
                    "polyline":"118.085,24.445;118.095,24.455"
                  }]}
                }
                """.formatted(road, status, speed);
    }
}
