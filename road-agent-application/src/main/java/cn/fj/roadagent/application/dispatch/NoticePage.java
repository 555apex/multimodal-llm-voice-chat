package cn.fj.roadagent.application.dispatch;

import java.time.Instant;
import java.util.List;

/** 通告列表只返回摘要，完整快照通过详情接口读取。 */
public record NoticePage(List<Item> items, int page, int size, long total,
                         long pendingCount, long completedCount) {
    public record Item(String workflowId, String eventId, String eventType,
                       String cityName, String place, String noticeNumber,
                       Instant publishedAt, String workflowStatus, String completionStatus) {}
}
