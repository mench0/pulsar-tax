package am.pulsar.tax.dto.patch;import am.pulsar.tax.dto.BusinessDto;
import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.TaxRegime;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Optional;

/** Изменение профиля ИП. Профиль один на пользователя, id подставляет сервер. */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class BusinessPatchDto extends BasePatchDto<BusinessDto> {

    Optional<@Size(max = 200, message = "Поле «name» длиннее 200 символов") String> name;

    Optional<@Size(max = 20, message = "Поле «tin» длиннее 20 символов") String> tin;

    Optional<@Size(max = 300, message = "Поле «address» длиннее 300 символов") String> address;

    Optional<@Size(max = 50, message = "Поле «bankAccount» длиннее 50 символов") String> bankAccount;

    Optional<TaxRegime> regime;

    Optional<ActivityType> activity;

    /** Ставка в процентах для своего вида деятельности (CUSTOM). */
    Optional<@DecimalMin(value = "0", message = "Ставка должна быть от 0 до 100%")
    @DecimalMax(value = "100", message = "Ставка должна быть от 0 до 100%") BigDecimal> customRate;
}
