package cn.fj.roadagent.core.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenActionClassifierTest {
    @Test
    void recognizesOnlyExplicitMonitorCommand() {
        var command = ScreenActionClassifier.classify("打开福州南监控").orElseThrow();
        assertEquals("OPEN_MONITOR", command.action());
        assertEquals("福州南", command.params().get("name"));
        assertFalse(ScreenActionClassifier.classify("福州南监控有多少台？").isPresent());
    }

    @Test
    void mapResourcesAreRestrictedToOrdinaryAllowlist() {
        var command = ScreenActionClassifier.classify("显示地图上的桥梁和应急队伍").orElseThrow();
        assertEquals("SET_MAP_RESOURCES", command.action());
        assertTrue(command.params().toString().contains("bridge"));
        assertTrue(command.params().toString().contains("team"));
        var blocked = ScreenActionClassifier.classify("开启全部资源和蜂鸣").orElseThrow();
        assertNull(blocked.action());
    }

    @Test
    void nearbySearchNeverInventsCoordinates() {
        var missing = ScreenActionClassifier.classify("搜索附近应急资源").orElseThrow();
        assertNull(missing.action());
        var located = ScreenActionClassifier.classify("搜索附近资源，经度119.29734纬度26.06979").orElseThrow();
        assertEquals("SEARCH_NEARBY_RESOURCES", located.action());
        assertEquals(119.29734, located.params().get("lng"));
        assertEquals(26.06979, located.params().get("lat"));
    }

    @Test
    void existingTrafficQuestionIsNotACommand() {
        assertFalse(ScreenActionClassifier.classify("福建省普通国省道当前运行情况如何？").isPresent());
        assertFalse(ScreenActionClassifier.classify("如何打开现场指挥界面？").isPresent());
        assertFalse(ScreenActionClassifier.classify("不要打开现场指挥界面").isPresent());
        assertFalse(ScreenActionClassifier.classify("能否显示桥梁资源？").isPresent());
    }
}
