package am.pulsar.tax.dto.patch;import am.pulsar.tax.dto.TaxPaymentDto;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
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

/** Изменение уплаты налога. */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class TaxPaymentPatchDto extends BasePatchDto<TaxPaymentDto> {

    Optional<LocalDate> date;

    Optional<@Positive(message = "Сумма уплаты должна быть больше нуля")
    @Digits(integer = 12, fraction = 2, message = "Сумма уплаты: не больше 2 знаков после запятой") BigDecimal> amount;
}
