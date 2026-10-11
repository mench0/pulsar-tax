package am.pulsar.tax.app.service.calculation;

/**
 * Нормативное основание значения в налоговых правилах и степень его проверки.
 *
 * <p>Pulsar не выдаёт предположения за закон: у каждого значения есть ссылка и честный статус.
 * Текст Налогового кодекса РА (arlis.am) на момент добавления правил не был доступен для сверки,
 * поэтому ни одно значение пока не имеет статуса {@link Status#OFFICIAL_TEXT}.
 */
public record LegalBasis(String reference, String url, Status status, String note) {

    public enum Status {
        /** Сверено с действующей редакцией закона на arlis.am. */
        OFFICIAL_TEXT,
        /** Подтверждено официальным государственным источником (пересказ закона, не сам текст). */
        OFFICIAL_SUMMARY,
        /** Согласованно в нескольких независимых профессиональных обзорах; текст закона не сверялся. */
        SECONDARY_SOURCES,
        /** Источники расходятся или значение не найдено: используется как допущение. */
        UNCONFIRMED
    }

    /** Нужно ли предупреждать пользователя, что значение не сверено с законом. */
    public boolean needsConfirmation() {
        return status != Status.OFFICIAL_TEXT;
    }

    /** Налоговый кодекс РА от 04.10.2016 ՀՕ-165-Ն в официальной базе правовых актов. */
    public static final String TAX_CODE = "Налоговый кодекс РА от 04.10.2016 ՀՕ-165-Ն";
    public static final String TAX_CODE_URL = "https://www.arlis.am/hy/acts/109017";
}
