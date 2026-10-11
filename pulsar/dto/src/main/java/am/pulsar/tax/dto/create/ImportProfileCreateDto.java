package am.pulsar.tax.dto.create;import am.pulsar.tax.dto.enums.Currency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Колонки банковской выписки: номера с 0, -1 — колонки нет. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportProfileCreateDto {

    @NotNull(message = "Неверная колонка «date»")
    @Min(value = -1, message = "Неверная колонка «date»")
    @Max(value = 200, message = "Неверная колонка «date»")
    Integer date;

    @NotNull(message = "Неверная колонка «amount»")
    @Min(value = -1, message = "Неверная колонка «amount»")
    @Max(value = 200, message = "Неверная колонка «amount»")
    Integer amount;

    @NotNull(message = "Неверная колонка «debit»")
    @Min(value = -1, message = "Неверная колонка «debit»")
    @Max(value = 200, message = "Неверная колонка «debit»")
    Integer debit;

    @NotNull(message = "Неверная колонка «desc»")
    @Min(value = -1, message = "Неверная колонка «desc»")
    @Max(value = 200, message = "Неверная колонка «desc»")
    Integer desc;

    @NotNull(message = "Неверная колонка «currencyCol»")
    @Min(value = -1, message = "Неверная колонка «currencyCol»")
    @Max(value = 200, message = "Неверная колонка «currencyCol»")
    Integer currencyCol;

    @NotBlank(message = "Заполните поле «dateFormat»")
    @Pattern(regexp = "dd\\.mm\\.yyyy|yyyy-mm-dd|mm/dd/yyyy", message = "Неизвестный формат даты: ${validatedValue}")
    String dateFormat;

    @NotNull(message = "Укажите «currency»")
    Currency currency;
}
