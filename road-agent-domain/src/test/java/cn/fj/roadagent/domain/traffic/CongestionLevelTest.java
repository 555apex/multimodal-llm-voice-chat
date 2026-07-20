package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CongestionLevelTest {

    @Test
    void shouldFindWorstCongestionLevel() {
        List<RoadSegmentStatus> segments = List.of(
                new RoadSegmentStatus("五四路", "南向北", CongestionLevel.SMOOTH, 42.0, null),
                new RoadSegmentStatus("五四路", "北向南", CongestionLevel.CONGESTED, 12.0, null)
        );

        assertEquals(CongestionLevel.CONGESTED, CongestionLevel.worstOf(segments));
    }
}
