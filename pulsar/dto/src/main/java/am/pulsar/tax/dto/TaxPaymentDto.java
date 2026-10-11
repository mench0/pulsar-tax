package am.pulsar.tax.dto;import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Уплата налога за квартал (у микробизнеса — за год, quarter = 0). */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaxPaymentDto extends BaseDto {

    Integer year;

    Integer quarter;

    LocalDate date;

    BigDecimal amount;
}
