package am.pulsar.tax.dto.patch;import am.pulsar.tax.dto.BaseDto;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Частичное обновление: поле null — не менять, Optional.empty() (в JSON — null) — очистить,
 * Optional.of(значение) — записать значение.
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BasePatchDto<D extends BaseDto> {

    @NotNull(message = "Укажите «id»")
    Long id;
}
