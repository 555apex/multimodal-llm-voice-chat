package cn.fj.roadagent.adapters.traffic.amap;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapTrafficDataAdapterTest {

    private MockRestServiceServer server;
    private AmapTrafficDataAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AmapTrafficDataAdapter(
                builder.build(),
                "https://restapi.amap.com/v3/traffic/status/road",
                "test-key",
                5,
                Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldMapAmapResponseToInternalModel() {
        server.expect(once(), requestTo(containsString("name=%E4%BA%94%E5%9B%9B%E8%B7%AF")))
                .andRespond(withSuccess("""
                        {
                          "status":"1",
                          "info":"OK",
                          "infocode":"10000",
                          "trafficinfo":{
                            "description":"五四路总体缓行",
                            "roads":[{
                              "name":"五四路",
                              "status":"2",
                              "direction":"南向北",
                              "speed":"25",
                              "polyline":"119.3,26.1;119.4,26.2"
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var snapshot = adapter.query(new TrafficQuery("350100", "五四路", "南向北"));

        assertEquals("AMAP", snapshot.source());
        assertEquals(CongestionLevel.SLOW, snapshot.segments().get(0).congestionLevel());
        assertEquals(25.0, snapshot.segments().get(0).averageSpeedKmh());
        server.verify();
    }

    @Test
    void shouldExposePermissionError() {
        server.expect(requestTo(containsString("adcode=350100")))
                .andRespond(withSuccess("""
                        {"status":"0","info":"USERKEY_PLAT_NOMATCH","infocode":"10009"}
                        """, MediaType.APPLICATION_JSON));

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> adapter.query(new TrafficQuery("350100", "五四路", null)));

        assertEquals("AMAP_AUTH_OR_PERMISSION_ERROR", exception.errorCode());
    }
}
