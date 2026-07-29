package cn.fj.roadagent.application.port;

import java.util.function.Supplier;

/** 让核心层声明事务边界，同时不依赖Spring事务类型。 */
public interface UnitOfWork {
    <T> T required(Supplier<T> operation);

    default void required(Runnable operation) {
        required(() -> {
            operation.run();
            return null;
        });
    }
}
