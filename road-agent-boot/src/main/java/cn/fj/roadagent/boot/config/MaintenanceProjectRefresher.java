package cn.fj.roadagent.boot.config;

import cn.fj.roadagent.application.port.MaintenanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class MaintenanceProjectRefresher {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceProjectRefresher.class);
    private final MaintenanceRepository repository;

    public MaintenanceProjectRefresher(MaintenanceRepository repository) {
        this.repository = repository;
    }

    public void start() {
        refresh();
    }

    @Scheduled(cron = "0 10 2 * * *", zone = "Asia/Shanghai")
    public void refresh() {
        try {
            repository.refreshProjects();
        } catch (RuntimeException exception) {
            log.warn("Maintenance project refresh failed; keeping previous snapshot", exception);
        }
    }
}
