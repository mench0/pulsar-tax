package am.pulsar.tax.app.entity;import am.pulsar.tax.dto.enums.Currency;
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

/** Поступление. Налог и порог считаются от суммы в драмах (amount). */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "incomes")
public class Income extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    LocalDate date;

    /** Сумма в драмах. */
    @Column(nullable = false)
    BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    Currency currency = Currency.AMD;

    /** Сумма в валюте и курс к драму; null у поступлений в драмах. */
    BigDecimal originalAmount;

    BigDecimal rate;

    @Column(nullable = false)
    @Builder.Default
    String client = "";

    @Column(nullable = false)
    @Builder.Default
    String description = "";

    /** Счёт, по которому пришла оплата. */
    Long invoiceId;

    /** Отпечаток строки выписки; null у записей, введённых вручную. */
    String importKey;
}
