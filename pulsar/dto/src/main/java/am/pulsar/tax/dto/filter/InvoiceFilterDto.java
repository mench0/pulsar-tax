package am.pulsar.tax.dto.filter;import am.pulsar.tax.dto.InvoiceDto;
import am.pulsar.tax.dto.enums.InvoiceStatus;
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
public class InvoiceFilterDto extends BaseFilterDto<InvoiceDto, InvoiceFilterDto> {

    /** Год даты счёта. */
    Integer year;

    /** Статусы (ИЛИ). */
    Set<InvoiceStatus> statuses;

    /** Номера (ИЛИ, без учёта регистра). */
    Set<String> numbers;

    /** Часть имени клиента (без учёта регистра). */
    String client;
}
