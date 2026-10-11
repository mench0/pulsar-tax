package am.pulsar.tax.app.exception;import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.entity.TaxPayment;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/** Запись не найдена или принадлежит другому пользователю (404). */
@Getter
public class EntityNotFoundByIdException extends ApiException {

    private static final Map<Class<?>, String> MESSAGES = Map.of(
            Income.class, "Запись о доходе не найдена",
            Expense.class, "Запись о расходе не найдена",
            TaxPayment.class, "Запись об уплате не найдена",
            Invoice.class, "Счёт не найден");

    private final Class<?> entityClass;
    private final Set<Long> ids;

    public EntityNotFoundByIdException(Class<?> entityClass, Long id) {
        this(entityClass, Set.of(id));
    }

    public EntityNotFoundByIdException(Class<?> entityClass, Set<Long> ids) {
        super(404, MESSAGES.getOrDefault(entityClass, "Запись не найдена"));
        this.entityClass = entityClass;
        this.ids = ids;
    }
}
