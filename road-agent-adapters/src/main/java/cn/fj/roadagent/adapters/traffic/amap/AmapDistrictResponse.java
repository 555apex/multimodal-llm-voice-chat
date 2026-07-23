package cn.fj.roadagent.adapters.traffic.amap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AmapDistrictResponse(
        String status,
        String info,
        String infocode,
        List<District> districts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record District(
            String name,
            String adcode,
            String level,
            String polyline,
            List<District> districts
    ) {
    }
}
