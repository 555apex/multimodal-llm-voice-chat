package cn.fj.roadagent.application.traffic;

public record RegionInsight(String regionCode, String interpretation) {
    public RegionInsight {
        regionCode = regionCode == null ? "" : regionCode.trim();
        interpretation = interpretation == null ? "" : interpretation.trim();
    }
}
