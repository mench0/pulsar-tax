package am.pulsar.tax.dto.create;import am.pulsar.tax.dto.IncomeDto;
import am.pulsar.tax.dto.enums.Currency;
import jakarta.validation.constraints.NotNull;
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

/**
 * Доход. В драмах передаётся amount; в другой валюте — originalAmount и rate (драмов за единицу),
 * а сумма в драмах считается сервером и округляется до драма.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class IncomeCreateDto implements BaseCreateDto<IncomeDto> {

    @NotNull(message = "Укажите дату «date»")
    LocalDate date;

    /** Валюта; по умолчанию AMD. */
    Currency currency;

    /** Сумма в драмах (для AMD). */
    BigDecimal amount;

    /** Сумма в валюте (кроме AMD). */
    BigDecimal originalAmount;

    /** Курс ЦБ РА (кроме AMD). */
    BigDecimal rate;

    @Size(max = 200, message = "Поле «client» длиннее 200 символов")
    String client;

    @Size(max = 500, message = "Поле «description» длиннее 500 символов")
    String description;

    /** Отпечаток строки выписки: повторная загрузка той же строки пропускается. */
    @Size(max = 100, message = "Поле «importKey» длиннее 100 символов")
    String importKey;
}
