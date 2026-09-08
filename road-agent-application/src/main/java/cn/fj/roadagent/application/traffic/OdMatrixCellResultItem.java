package cn.fj.roadagent.application.traffic;

/** 城市联系矩阵的一个目标城市单元格；无路线连接时数值为空。 */
public record OdMatrixCellResultItem(
        String destinationRegionCode,
        String destinationCityName,
        Double weeklyConnectionStrength,
        Double tendencyRatio
) { }
