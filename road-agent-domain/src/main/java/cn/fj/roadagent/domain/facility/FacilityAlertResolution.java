package cn.fj.roadagent.domain.facility;

public enum FacilityAlertResolution {
    RESOLVED("已消除"),
    IGNORED("已忽略");

    private final String displayName;

    FacilityAlertResolution(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
