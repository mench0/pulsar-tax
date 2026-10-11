package am.pulsar.tax.dto.bank;

import java.util.List;

/** Предварительный просмотр выписки: строки и итоги проверки. Ничего не сохраняется. */
public record StatementPreviewDto(
        int total,
        int valid,
        int errors,
        int duplicates,
        int needsReview,
        List<StatementRowDto> rows,
        /** Номер ранее загруженной выписки с тем же файлом (по SHA-256), если есть. */
        Long sameFileImportId) {
}
