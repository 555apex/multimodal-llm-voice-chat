package cn.fj.roadagent.application.traffic;

@FunctionalInterface
public interface AreaTrafficProgressListener {
    void onProgress(AreaTrafficProgress progress);

    static AreaTrafficProgressListener noOp() {
        return ignored -> { };
    }
}
