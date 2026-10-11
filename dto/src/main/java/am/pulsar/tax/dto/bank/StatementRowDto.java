package am.pulsar.tax.dto.bank;

import am.pulsar.tax.dto.enums.TransactionKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Строка выписки после проверки: разобранные значения, ошибки (строку нельзя сохранить),
 * предупреждения, признак дубликата и предложенный тип с уверенностью и причинами.
 */
public record StatementRowDto(
        int row,
        LocalDate date,
        BigDecimal amount,
        String currency,
        String counterparty,
        String description,
        String reference,
        BigDecimal rate,
        String rateSource,
        BigDecimal amountAmd,
        TransactionKind suggestedKind,
        BigDecimal confidence,
        List<String> reasons,
        List<String> errors,
        List<String> warnings,
        boolean duplicate,
        String duplicateOf,
        String fingerprint) {
}
