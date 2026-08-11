package cn.fj.roadagent.core.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechTextSanitizerTest {

    @Test
    void shouldOmitTablesCodeAndUrls() {
        String result = SpeechTextSanitizer.toSpeakableText("""
                当前道路整体通行正常。
                | 道路 | 速度 |
                | --- | --- |
                | 五四路 | 30 |
                ```json
                {"speed": 30}
                ```
                详情：https://example.com/data
                """);

        assertTrue(result.startsWith("当前道路整体通行正常"));
        assertFalse(result.contains("五四路"));
        assertFalse(result.contains("https"));
        assertTrue(result.endsWith("详细数据请查看页面。"));
    }

    @Test
    void shouldOmitLongListsButKeepShortListsReadable() {
        assertEquals(
                "建议。详细数据请查看页面。",
                SpeechTextSanitizer.toSpeakableText("""
                        建议
                        1. 第一项
                        2. 第二项
                        3. 第三项
                        4. 第四项
                        """)
        );
        assertEquals(
                "第一项。第二项。",
                SpeechTextSanitizer.toSpeakableText("""
                        - 第一项
                        - 第二项
                        """)
        );
    }
}
