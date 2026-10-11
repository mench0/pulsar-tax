package am.pulsar.tax.app.entity;

import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.TransactionKind;
import am.pulsar.tax.dto.enums.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Банковская операция. amount со знаком: плюс — поступление, минус — списание. */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "bank_transactions")
public class BankTransaction extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    Long importId;

    Integer rowNumber;

    @Column(nullable = false)
    LocalDate date;

    @Column(nullable = false)
    BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    Currency currency = Currency.AMD;

    /** Курс к драму (у операций в драмах — null). */
    BigDecimal rate;

    /** Сумма в драмах со знаком; null, если курс неизвестен. */
    BigDecimal amountAmd;

    @Column(nullable = false)
    @Builder.Default
    String counterparty = "";

    @Column(nullable = false)
    @Builder.Default
    String description = "";

    @Column(nullable = false)
    @Builder.Default
    String reference = "";

    @Column(nullable = false)
    @Builder.Default
    String account = "";

    @Column(nullable = false)
    String fingerprint;

    String legacyKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    TransactionKind suggestedKind;

    @Column(nullable = false)
    BigDecimal confidence;

    /** Коды причин классификации через запятую. */
    @Column(nullable = false)
    @Builder.Default
    String reasons = "";

    @Enumerated(EnumType.STRING)
    TransactionKind kind;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    TransactionStatus status = TransactionStatus.NEEDS_REVIEW;

    Long incomeId;

    Long expenseId;

    Long taxPaymentId;

    Long linkedTransactionId;

    /** Ячейки исходной строки (JSON) — для проверки и повторной обработки. */
    @Column(nullable = false)
    @Builder.Default
    String raw = "[]";

    public boolean incoming() {
        return amount.signum() > 0;
    }
}
