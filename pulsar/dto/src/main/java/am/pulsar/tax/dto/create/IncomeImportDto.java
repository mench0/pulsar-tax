package am.pulsar.tax.dto.create;import am.pulsar.tax.dto.IncomeDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.util.List;

/** Пакет доходов из банковской выписки. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class IncomeImportDto implements BaseCreateDto<IncomeDto> {

    /** Строки выписки; каждая проверяется как отдельный доход. */
    @NotNull(message = "Поле «items» должно быть списком")
    @Size(max = 5000, message = "За раз можно загрузить не больше 5000 строк")
    List<IncomeCreateDto> items;
}
