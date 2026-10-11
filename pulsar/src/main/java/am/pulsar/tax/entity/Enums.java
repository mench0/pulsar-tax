package am.pulsar.tax.entity;

/** Перечисления предметной области. Хранятся в базе строками (имя константы). */
public final class Enums {

    private Enums() {
    }

    public enum TaxRegime {
        /** Микробизнес: оборот до порога, налог с дохода не платится. */
        MICRO,
        /** Налог с оборота: ставка зависит от вида деятельности. */
        TURNOVER,
        /** Общая система (НДС и подоходный налог) — не рассчитывается. */
        GENERAL
    }

    /** Вид деятельности: от него зависят ставка налога с оборота и доступные режимы. */
    public enum ActivityType {
        TRADE, SERVICES, HIGH_TECH,
        CONSULTING, LEGAL, ACCOUNTING, ENGINEERING, MARKETING, DESIGN, TRANSLATION, AGENCY,
        CUSTOM
    }

    public enum InvoiceStatus { SENT, PAID }

    /** Валюты поступлений; налог считается в драмах по курсу на дату поступления. */
    public enum Currency { AMD, USD, EUR, RUB, GBP, GEL }

    public enum ExpenseCategory { RENT, EQUIPMENT, SOFTWARE, SERVICES, TRANSPORT, BANK_FEES, TAXES_FEES, OTHER }
}
