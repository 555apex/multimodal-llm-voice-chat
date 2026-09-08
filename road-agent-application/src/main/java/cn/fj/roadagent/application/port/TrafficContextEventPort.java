package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.TrafficContextEvent;

import java.time.Instant;
import java.util.List;

/** 按交通数据时间读取正在生效的节假日与重大活动。 */
@FunctionalInterface
public interface TrafficContextEventPort {
    List<TrafficContextEvent> findActiveAt(Instant trafficDataTime);
}
