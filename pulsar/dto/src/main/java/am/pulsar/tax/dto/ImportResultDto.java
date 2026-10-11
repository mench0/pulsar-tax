package am.pulsar.tax.dto;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Итог загрузки выписки. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportResultDto {

    /** Добавлено строк. */
    int added;

    /** Пропущено уже загруженных строк. */
    int skipped;
}
