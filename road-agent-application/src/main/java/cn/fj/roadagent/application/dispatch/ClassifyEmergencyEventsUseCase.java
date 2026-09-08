package cn.fj.roadagent.application.dispatch;

public interface ClassifyEmergencyEventsUseCase {
    /** 处理一条待分类事件，无待办时返回false。 */
    boolean classifyNext();

    /** 忽略自动退避时间，立即重试当前最早的一条待分类事件。 */
    boolean retryNext();

    void retry(String eventId);
}
