package cn.fj.roadagent.domain.dispatch;

/**
 * 应急资源
 * 具体：某类应急资源应具备以下成员变量
 * 备注：Mock资源和未来甲方资源都转换为该标准对象。 */
public record EmergencyResource(
        String resourceId,  // 资源ID
        String type,    // 资源类型
        String name,    // 资源名称
        String city,    // 资源所在城市
        String capability,  // 能力描述：道路抢通、边坡处置等
        boolean available   // 资源可用性
) {
}
