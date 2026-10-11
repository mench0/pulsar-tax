package am.pulsar.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Модель данных Pulsar Tax и её перевод в JSON. */
public final class Model {

    private Model() {
    }

    public enum TaxRegime {
        /** Микробизнес: оборот до порога, налог с дохода не платится. */
        MICRO,
        /** Налог с оборота: ставка зависит от вида деятельности. */
        TURNOVER,
        /** Общая система (НДС и подоходный налог) — в MVP не рассчитывается. */
        GENERAL
    }

    /** Вид деятельности. От него зависят ставка налога с оборота и доступные режимы (см. TaxRules). */
    public enum ActivityType {
        TRADE, SERVICES, HIGH_TECH,
        CONSULTING, LEGAL, ACCOUNTING, ENGINEERING, MARKETING, DESIGN, TRANSLATION, AGENCY,
        CUSTOM
    }

    public enum InvoiceStatus { SENT, PAID }

    /** Валюты поступлений. Налог считается в драмах по курсу на дату поступления. */
    public enum Currency { AMD, USD, EUR, RUB, GBP, GEL }

    /** Профиль ИП. Один на учётную запись. */
    public static final class Profile {
        public String name = "";
        public String tin = "";
        public String address = "";
        public String bankAccount = "";
        public TaxRegime regime = TaxRegime.MICRO;
        public ActivityType activity = ActivityType.SERVICES;
        /** Ставка в процентах для ActivityType.CUSTOM. */
        public BigDecimal customRate;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("tin", tin);
            m.put("address", address);
            m.put("bankAccount", bankAccount);
            m.put("regime", regime);
            m.put("activity", activity);
            m.put("customRate", customRate);
            return m;
        }
    }

    public static final class Income {
        public long id;
        public LocalDate date;
        /** Сумма в драмах: от неё считаются налог и порог. */
        public BigDecimal amount;
        public Currency currency = Currency.AMD;
        /** Сумма в валюте поступления и курс к драму; null у поступлений в драмах. */
        public BigDecimal originalAmount;
        public BigDecimal rate;
        public String client = "";
        public String description = "";
        /** Счёт, по которому пришла оплата, или null. */
        public Long invoiceId;
        /** Отпечаток строки выписки, чтобы повторный импорт не создал дубль; null у ручных записей. */
        public String importKey;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("date", date);
            m.put("amount", amount);
            m.put("currency", currency);
            m.put("originalAmount", originalAmount);
            m.put("rate", rate);
            m.put("client", client);
            m.put("description", description);
            m.put("invoiceId", invoiceId);
            m.put("importKey", importKey);
            return m;
        }
    }

    public record Item(String name, BigDecimal quantity, BigDecimal price) {
        /** Сумма позиции, округлённая до драма. */
        BigDecimal sum() {
            return quantity.multiply(price).setScale(0, RoundingMode.HALF_UP);
        }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("quantity", quantity);
            m.put("price", price);
            m.put("sum", sum());
            return m;
        }
    }

    /**
     * Счёт на оплату. Это не налоговый документ: официальный электронный счёт выписывается
     * в системе e-invoice.taxservice.am, его номер хранится в eInvoiceNumber.
     */
    public static final class Invoice {
        public long id;
        public String number;
        public LocalDate date;
        public LocalDate dueDate;
        public String client;
        public String clientTin = "";
        public InvoiceStatus status = InvoiceStatus.SENT;
        public String eInvoiceNumber = "";
        public List<Item> items = new ArrayList<>();

        BigDecimal total() {
            return items.stream().map(Item::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("number", number);
            m.put("date", date);
            m.put("dueDate", dueDate);
            m.put("client", client);
            m.put("clientTin", clientTin);
            m.put("status", status);
            m.put("eInvoiceNumber", eInvoiceNumber);
            m.put("items", items.stream().map(Item::toJson).toList());
            m.put("total", total());
            return m;
        }
    }

    public enum ExpenseCategory { RENT, EQUIPMENT, SOFTWARE, SERVICES, TRANSPORT, BANK_FEES, TAXES_FEES, OTHER }

    /**
     * Расход ИП. Пока только учёт и сводка: на налог не влияет, потому что правила вычета расходов
     * из налога с оборота нужно сначала сверить с Налоговым кодексом.
     */
    public static final class Expense {
        public long id;
        public LocalDate date;
        /** Сумма в драмах. */
        public BigDecimal amount;
        public ExpenseCategory category = ExpenseCategory.OTHER;
        public String description = "";

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("date", date);
            m.put("amount", amount);
            m.put("category", category);
            m.put("description", description);
            return m;
        }
    }

    /** Уплата налога за квартал (у микробизнеса — за год, quarter = 0). */
    public static final class TaxPayment {
        public long id;
        public int year;
        public int quarter;
        public LocalDate date;
        public BigDecimal amount;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("year", year);
            m.put("quarter", quarter);
            m.put("date", date);
            m.put("amount", amount);
            return m;
        }
    }
}
