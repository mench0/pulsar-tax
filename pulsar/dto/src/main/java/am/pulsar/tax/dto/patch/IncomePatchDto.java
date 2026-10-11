package am.pulsar.tax.dto.patch;import am.pulsar.tax.dto.IncomeDto;
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

/** Изменение дохода. Сумму в драмах можно менять только у поступлений в драмах. */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class IncomePatchDto extends BasePatchDto<IncomeDto> {

    Optional<LocalDate> date;

    Optional<@Positive(message = "Сумма дохода должна быть больше нуля")
    @Digits(integer = 12, fraction = 2, message = "Сумма дохода: не больше 2 знаков после запятой") BigDecimal> amount;

    Optional<@Size(max = 200, message = "Поле «client» длиннее 200 символов") String> client;

    Optional<@Size(max = 500, message = "Поле «description» длиннее 500 символов") String> description;
}
