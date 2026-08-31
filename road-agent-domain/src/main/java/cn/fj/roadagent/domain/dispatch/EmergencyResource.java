package cn.fj.roadagent.domain.dispatch;

import java.util.List;
import java.util.Objects;

/** 城市级聚合资源池；数据库库存数量是正式调度的唯一可信来源。 */
public record EmergencyResource(
        String resourceId,
        String typeCode,
        String type,
        String name,
        String cityCode,
        String city,
        String unit,
        String capability,
        List<String> applicableEventTypes,
        int totalQuantity,
        int availableQuantity,
        int reservedQuantity,
        int dispatchedQuantity,
        int minimumReserveQuantity,
        EmergencyResourceStatus status,
        long lockVersion
) {
    public EmergencyResource {
        resourceId = requireText(resourceId, "资源ID不能为空");
        typeCode = requireText(typeCode, "资源类型编码不能为空");
        type = requireText(type, "资源类型不能为空");
        name = requireText(name, "资源名称不能为空");
        cityCode = requireText(cityCode, "资源城市编码不能为空");
        city = requireText(city, "资源城市不能为空");
        unit = requireText(unit, "资源单位不能为空");
        capability = requireText(capability, "资源能力描述不能为空");
        applicableEventTypes = applicableEventTypes == null
                ? List.of() : List.copyOf(applicableEventTypes);
        status = Objects.requireNonNull(status, "资源状态不能为空");
        if (totalQuantity < 0 || availableQuantity < 0 || reservedQuantity < 0
                || dispatchedQuantity < 0 || minimumReserveQuantity < 0 || lockVersion < 0) {
            throw new IllegalArgumentException("资源数量和版本不能为负数");
        }
        if (availableQuantity + reservedQuantity + dispatchedQuantity > totalQuantity) {
            throw new IllegalArgumentException("资源可用、预留和已调度数量之和不能超过总量");
        }
        if (minimumReserveQuantity > totalQuantity) {
            throw new IllegalArgumentException("跨市最低保有量不能超过资源总量");
        }
    }

    /** 兼容早期Mock对象；正式数据库适配器使用完整构造参数。 */
    public EmergencyResource(
            String resourceId,
            String type,
            String name,
            String city,
            String capability,
            boolean available
    ) {
        this(resourceId, type, type, name, "000000", city, "项", capability,
                List.of(), 1, available ? 1 : 0, 0, 0, 0,
                available ? EmergencyResourceStatus.ACTIVE : EmergencyResourceStatus.DISABLED, 0);
    }

    public boolean available() {
        return status == EmergencyResourceStatus.ACTIVE && availableQuantity > 0;
    }

    public boolean appliesTo(String eventType) {
        return applicableEventTypes.isEmpty() || applicableEventTypes.contains(eventType);
    }

    public EmergencyResource reserve(int quantity) {
        requirePositive(quantity);
        if (status != EmergencyResourceStatus.ACTIVE || availableQuantity < quantity) {
            throw new IllegalStateException("资源可用数量不足");
        }
        return quantities(availableQuantity - quantity, reservedQuantity + quantity,
                dispatchedQuantity);
    }

    public EmergencyResource dispatch(int quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new IllegalStateException("资源预留数量不足");
        }
        return quantities(availableQuantity, reservedQuantity - quantity,
                dispatchedQuantity + quantity);
    }

    public EmergencyResource releaseReserved(int quantity) {
        requirePositive(quantity);
        if (reservedQuantity < quantity) {
            throw new IllegalStateException("待释放的预留资源数量不足");
        }
        return quantities(availableQuantity + quantity, reservedQuantity - quantity,
                dispatchedQuantity);
    }

    public EmergencyResource releaseDispatched(int quantity) {
        requirePositive(quantity);
        if (dispatchedQuantity < quantity) {
            throw new IllegalStateException("待归还的已调度资源数量不足");
        }
        return quantities(availableQuantity + quantity, reservedQuantity,
                dispatchedQuantity - quantity);
    }

    private EmergencyResource quantities(int available, int reserved, int dispatched) {
        return new EmergencyResource(
                resourceId, typeCode, type, name, cityCode, city, unit, capability,
                applicableEventTypes, totalQuantity, available, reserved, dispatched,
                minimumReserveQuantity, status, lockVersion + 1
        );
    }

    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("资源操作数量必须大于0");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
