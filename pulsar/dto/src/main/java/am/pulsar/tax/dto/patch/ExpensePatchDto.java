package am.pulsar.tax.dto.patch;import am.pulsar.tax.dto.ExpenseDto;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Изменение расхода. */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class ExpensePatchDto extends BasePatchDto<ExpenseDto> {

    Optional<LocalDate> date;

    Optional<@Positive(message = "Сумма расхода должна быть больше нуля")
    @Digits(integer = 12, fraction = 2, message = "Сумма расхода: не больше 2 знаков после запятой") BigDecimal> amount;

    Optional<ExpenseCategory> category;

    Optional<@Size(max = 500, message = "Поле «description» длиннее 500 символов") String> description;
}
