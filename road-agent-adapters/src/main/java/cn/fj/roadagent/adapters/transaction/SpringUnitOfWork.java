package cn.fj.roadagent.adapters.transaction;

import cn.fj.roadagent.application.port.UnitOfWork;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Spring事务适配器，避免核心业务依赖Spring事务API。 */
public final class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transactionTemplate;

    public SpringUnitOfWork(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T required(Supplier<T> operation) {
        return transactionTemplate.execute(status -> operation.get());
    }
}
