package am.pulsar.tax.dto;import am.pulsar.tax.dto.enums.Currency;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Сохранённые колонки банковской выписки (по отпечатку заголовка). Номера колонок с 0, -1 — колонки нет.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportProfileDto {

    /** Колонка даты. */
    Integer date;

    /** Колонка суммы поступления. */
    Integer amount;

    /** Колонка суммы списания. */
    Integer debit;

    /** Колонка назначения платежа. */
    Integer desc;

    /** Колонка валюты. */
    Integer currencyCol;

    /** Формат даты: dd.mm.yyyy, yyyy-mm-dd или mm/dd/yyyy. */
    String dateFormat;

    /** Валюта выписки, если колонки валюты нет. */
    Currency currency;
}
