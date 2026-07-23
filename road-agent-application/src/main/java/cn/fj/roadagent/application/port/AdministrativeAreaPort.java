package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.AdministrativeArea;

/** 使用可信行政区服务解析名称、adcode和边界。 */
@FunctionalInterface
public interface AdministrativeAreaPort {
    AdministrativeArea resolve(String city, String areaName);
}
