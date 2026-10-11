package am.pulsar.tax.dto.create;import am.pulsar.tax.dto.TaxPaymentDto;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Уплата налога. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class TaxPaymentCreateDto implements BaseCreateDto<TaxPaymentDto> {

    @NotNull(message = "Неверный год")
    @Min(value = 2000, message = "Неверный год")
    @Max(value = 2100, message = "Неверный год")
    Integer year;

    /** 1–4, 0 — за год целиком (микробизнес). */
    @NotNull(message = "Квартал должен быть от 1 до 4 (0 — за год)")
    @Min(value = 0, message = "Квартал должен быть от 1 до 4 (0 — за год)")
    @Max(value = 4, message = "Квартал должен быть от 1 до 4 (0 — за год)")
    Integer quarter;

    @NotNull(message = "Укажите дату «date»")
    LocalDate date;

    @NotNull(message = "Заполните поле «amount»")
    @Positive(message = "Сумма уплаты должна быть больше нуля")
    @Digits(integer = 12, fraction = 2, message = "Сумма уплаты: не больше 2 знаков после запятой")
    BigDecimal amount;
}
