package cn.fj.roadagent.application.traffic;

@FunctionalInterface
public interface QueryHighwayTrafficUseCase {
    HighwayTrafficResult query(HighwayTrafficQuery query);
}
