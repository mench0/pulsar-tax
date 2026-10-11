package am.pulsar.tax.dto;import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.TaxRegime;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/** Профиль ИП. */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BusinessDto extends BaseDto {

    String name;

    /** ИНН (ՀՎՀՀ). */
    String tin;

    String address;

    String bankAccount;

    TaxRegime regime;

    ActivityType activity;

    /** Ставка в процентах для ActivityType.CUSTOM. */
    BigDecimal customRate;
}
