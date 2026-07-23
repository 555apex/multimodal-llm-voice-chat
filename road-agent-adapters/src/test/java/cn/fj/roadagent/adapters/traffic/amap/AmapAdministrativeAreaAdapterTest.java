package cn.fj.roadagent.adapters.traffic.amap;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.domain.traffic.AdministrativeAreaLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapAdministrativeAreaAdapterTest {

    private MockRestServiceServer server;
    private AmapAdministrativeAreaAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AmapAdministrativeAreaAdapter(
                builder.build(), "https://restapi.amap.com/v3/config/district", "test-key",
                Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(24)
        );
    }

    @Test
    void shouldResolveDistrictAndReuseCachedBoundary() {
        server.expect(once(), requestTo(containsString("extensions=all")))
                .andRespond(withSuccess("""
                        {
                          "status":"1","info":"OK","infocode":"10000",
                          "districts":[{
                            "name":"厦门市","adcode":"350200","level":"city","polyline":"",
                            "districts":[{
                              "name":"思明区","adcode":"350203","level":"district",
                              "polyline":"118.0800,24.4400;118.1100,24.4400;118.1100,24.4700;118.0800,24.4700|118.1200,24.4500;118.1300,24.4500;118.1300,24.4600;118.1200,24.4600",
                              "districts":[]
                            }]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var first = adapter.resolve("厦门", "思明区");
        var second = adapter.resolve("厦门市", "思明");

        assertEquals("350203", first.adcode());
        assertEquals("厦门", first.city());
        assertEquals(AdministrativeAreaLevel.DISTRICT, first.level());
        assertEquals(2, first.boundary().polygons().size());
        assertEquals(first, second);
        server.verify();
    }

    @Test
    void shouldRejectAreaOutsideFujian() {
        server.expect(requestTo(containsString("keywords=%E6%B5%B7%E6%B7%80%E5%8C%BA")))
                .andRespond(withSuccess("""
                        {
                          "status":"1","info":"OK","infocode":"10000",
                          "districts":[{
                            "name":"海淀区","adcode":"110108","level":"district",
                            "polyline":"116.2,39.9;116.3,39.9;116.3,40.0;116.2,40.0"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                () -> adapter.resolve(null, "海淀区"));

        assertEquals("TRAFFIC_OUTSIDE_FUJIAN", exception.errorCode());
    }
}
