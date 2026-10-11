package am.pulsar.tax.dto.bank;

import java.math.BigDecimal;

/**
 * Решение пользователя по операции: подтвердить тип (kind), исключить (ignore=true) или вернуть на проверку
 * (reopen=true). Для операций в валюте без курса ЦБ в кэше нужен rate. Для уплаты налога — год и квартал (0 — за год).
 */
public record TransactionReviewDto(
        String kind,
        Boolean ignore,
        Boolean reopen,
        BigDecimal rate,
        Integer taxYear,
        Integer taxQuarter,
        String expenseCategory) {
}
