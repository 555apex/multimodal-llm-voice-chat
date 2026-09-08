package cn.fj.roadagent.application.traffic;

/** 单城市视角下的目的地联系倾向。 */
public record OdDestinationTendencyResultItem(
        String analysisRegionCode,
        String analysisCityName,
        String destinationRegionCode,
        String destinationCityName,
        int routeCount,
        double weeklyConnectionStrength,
        double tendencyRatio
) { }
