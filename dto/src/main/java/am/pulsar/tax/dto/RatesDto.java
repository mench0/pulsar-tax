package am.pulsar.tax.dto;import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Курсы ЦБ РА на дату: ISO-код валюты → драмов за 1 единицу. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RatesDto {

    LocalDate date;

    /** Источник курсов. */
    String source;

    Map<String, BigDecimal> rates;
}
