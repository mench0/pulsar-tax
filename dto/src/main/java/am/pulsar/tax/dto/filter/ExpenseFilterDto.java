package am.pulsar.tax.dto.filter;import am.pulsar.tax.dto.ExpenseDto;
import am.pulsar.tax.dto.enums.ExpenseCategory;
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
public class ExpenseFilterDto extends BaseFilterDto<ExpenseDto, ExpenseFilterDto> {

    /** Год даты расхода. */
    Integer year;

    /** Дата расхода не раньше. */
    LocalDate dateFrom;

    /** Дата расхода не позже. */
    LocalDate dateTo;

    /** Категории (ИЛИ). */
    Set<ExpenseCategory> categories;
}
