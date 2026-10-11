package am.pulsar.tax.dto.bank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Выписка, уже разбитая на ячейки (CSV разбирает клиент), и сопоставление колонок.
 * overrides — решения пользователя по строкам при сохранении (номер строки с 1).
 */
public record StatementUploadDto(
        String fileName,
        String fileSha256,
        /** Свой счёт, к которому относится выписка (номер или название); помогает распознать переводы между своими счетами. */
        String account,
        List<String> header,
        List<List<String>> rows,
        StatementMappingDto mapping,
        List<RowOverride> overrides) {

    /** Решение по строке: пропустить, задать тип (тогда операция сразу подтверждена) или курс. */
    public record RowOverride(Integer row, Boolean skip, String kind, BigDecimal rate) {
    }
}
