package am.pulsar.tax.dto.filter;import am.pulsar.tax.dto.BaseDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BaseFilterDto<D extends BaseDto, F extends BaseFilterDto<D, F>> {

    /** Идентификаторы (ИЛИ). */
    Set<Long> ids;

    /** Группировка нескольких фильтров через ИЛИ (формат вложенных фильтров такой же, как у основного). */
    @JsonProperty("_or_")
    Set<F> subFiltersAggregatedByOr;
}
