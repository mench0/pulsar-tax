package am.pulsar.tax.dto.filter;import am.pulsar.tax.dto.IncomeDto;
import am.pulsar.tax.dto.enums.Currency;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncomeFilterDto extends BaseFilterDto<IncomeDto, IncomeFilterDto> {

    /** Год даты поступления. */
    Integer year;

    /** Дата поступления не раньше. */
    LocalDate dateFrom;

    /** Дата поступления не позже. */
    LocalDate dateTo;

    /** Валюты (ИЛИ). */
    Set<Currency> currencies;

    /** Счета, по которым пришла оплата (ИЛИ). */
    Set<Long> invoiceIds;

    /** Отпечатки строк выписки (ИЛИ). */
    Set<String> importKeys;

    /** Часть имени клиента (без учёта регистра). */
    String client;
}
