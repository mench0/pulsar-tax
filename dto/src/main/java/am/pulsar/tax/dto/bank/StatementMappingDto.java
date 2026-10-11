package am.pulsar.tax.dto.bank;

/**
 * Какая колонка выписки что значит (-1 — колонки нет). Совместимо с сохранёнными профилями импорта:
 * date, amount, debit, desc, currencyCol, dateFormat, currency; дополнительно — контрагент, номер документа, счёт.
 */
public record StatementMappingDto(
        Integer date,
        String dateFormat,
        Integer amount,
        Integer debit,
        Integer desc,
        Integer currencyCol,
        String currency,
        Integer counterparty,
        Integer reference,
        Integer account) {

    public int col(Integer v) {
        return v == null ? -1 : v;
    }
}
