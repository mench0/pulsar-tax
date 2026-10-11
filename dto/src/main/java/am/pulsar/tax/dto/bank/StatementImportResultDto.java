package am.pulsar.tax.dto.bank;

/** Итог сохранения выписки. */
public record StatementImportResultDto(
        Long importId,
        int added,
        int duplicates,
        int errors,
        int skipped,
        int confirmed,
        int needsReview) {
}
