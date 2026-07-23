package cn.fj.roadagent.application.traffic;

@FunctionalInterface
public interface QueryAreaTrafficUseCase {
    AreaTrafficQueryResult queryArea(AreaTrafficQueryCommand command);
}
