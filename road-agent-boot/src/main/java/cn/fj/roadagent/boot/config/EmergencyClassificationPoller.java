package cn.fj.roadagent.boot.config;

import cn.fj.roadagent.application.dispatch.ClassifyEmergencyEventsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 独立小型轮询器，分类失败不会影响主服务启动和文本业务。 */
public final class EmergencyClassificationPoller implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(EmergencyClassificationPoller.class);
    private final ClassifyEmergencyEventsUseCase useCase;
    private final Duration interval;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "emergency-event-classifier");
        thread.setDaemon(true);
        return thread;
    });

    public EmergencyClassificationPoller(ClassifyEmergencyEventsUseCase useCase, Duration interval) {
        this.useCase = useCase;
        this.interval = interval;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::pollSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void pollSafely() {
        try {
            useCase.classifyNext();
        } catch (RuntimeException exception) {
            log.warn("应急事件自动分类失败，将按退避配置重试：{}", exception.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
