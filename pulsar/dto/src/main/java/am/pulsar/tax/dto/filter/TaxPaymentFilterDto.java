package am.pulsar.tax.dto.filter;import am.pulsar.tax.dto.TaxPaymentDto;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaxPaymentFilterDto extends BaseFilterDto<TaxPaymentDto, TaxPaymentFilterDto> {

    /** Налоговые годы (ИЛИ). */
    Set<Integer> years;

    /** Кварталы (ИЛИ), 0 — за год. */
    Set<Integer> quarters;
}
