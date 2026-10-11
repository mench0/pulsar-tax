package am.pulsar.tax.dto;import am.pulsar.tax.dto.enums.ExpenseCategory;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Расход. Пока только учёт и сводка: на налог не влияет. */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExpenseDto extends BaseDto {

    LocalDate date;

    /** Сумма в драмах. */
    BigDecimal amount;

    ExpenseCategory category;

    String description;
}
