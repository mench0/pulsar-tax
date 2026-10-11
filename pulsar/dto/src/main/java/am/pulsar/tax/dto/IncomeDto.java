package am.pulsar.tax.dto;import am.pulsar.tax.dto.enums.Currency;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Поступление. Налог и порог считаются от суммы в драмах (amount). */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncomeDto extends BaseDto {

    LocalDate date;

    /** Сумма в драмах. */
    BigDecimal amount;

    Currency currency;

    /** Сумма в валюте поступления; null у поступлений в драмах. */
    BigDecimal originalAmount;

    /** Курс ЦБ РА: драмов за единицу валюты; null у поступлений в драмах. */
    BigDecimal rate;

    String client;

    String description;

    /** Счёт, по которому пришла оплата. */
    Long invoiceId;

    /** Отпечаток строки банковской выписки; null у записей, введённых вручную. */
    String importKey;
}
