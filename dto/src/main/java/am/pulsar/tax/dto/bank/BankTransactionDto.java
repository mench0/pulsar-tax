package am.pulsar.tax.dto.bank;

import am.pulsar.tax.dto.enums.TransactionKind;
import am.pulsar.tax.dto.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Банковская операция в учёте. amount со знаком: плюс — поступление, минус — списание. */
public record BankTransactionDto(
        Long id,
        Long importId,
        Integer rowNumber,
        LocalDate date,
        BigDecimal amount,
        String currency,
        BigDecimal rate,
        BigDecimal amountAmd,
        String counterparty,
        String description,
        String reference,
        String account,
        TransactionKind suggestedKind,
        BigDecimal confidence,
        List<String> reasons,
        TransactionKind kind,
        TransactionStatus status,
        Long incomeId,
        Long expenseId,
        Long taxPaymentId,
        Long linkedTransactionId) {
}
