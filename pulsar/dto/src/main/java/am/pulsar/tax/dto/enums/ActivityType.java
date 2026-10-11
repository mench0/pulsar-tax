package am.pulsar.tax.dto.enums;

/** Вид деятельности. От него зависят ставка налога с оборота и доступные режимы. */
public enum ActivityType {
    TRADE, SERVICES, HIGH_TECH,
    CONSULTING, LEGAL, ACCOUNTING, ENGINEERING, MARKETING, DESIGN, TRANSLATION, AGENCY,
    CUSTOM
}
