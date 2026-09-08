package cn.fj.roadagent.application.traffic;

import java.util.List;

/** 固定全省分母下，一个分析城市对应的目的地联系倾向矩阵行。 */
public record OdMatrixRowResultItem(
        String analysisRegionCode,
        String analysisCityName,
        List<OdMatrixCellResultItem> cells
) {
    public OdMatrixRowResultItem {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
