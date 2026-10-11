package am.pulsar.tax.dto.create;import am.pulsar.tax.dto.ExpenseDto;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Расход. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class ExpenseCreateDto implements BaseCreateDto<ExpenseDto> {

    @NotNull(message = "Укажите дату «date»")
    LocalDate date;

    @NotNull(message = "Заполните поле «amount»")
    @Positive(message = "Сумма расхода должна быть больше нуля")
    @Digits(integer = 12, fraction = 2, message = "Сумма расхода: не больше 2 знаков после запятой")
    BigDecimal amount;

    /** Категория; по умолчанию OTHER. */
    ExpenseCategory category;

    @Size(max = 500, message = "Поле «description» длиннее 500 символов")
    String description;
}
