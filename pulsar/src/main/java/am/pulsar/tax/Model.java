package am.pulsar.tax;

import java.math.BigDecimal;
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

    public enum ActivityType { TRADE, SERVICES, HIGH_TECH, CUSTOM }

    public enum InvoiceStatus { SENT, PAID }

    /** Профиль ИП. Один на установку. */
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
        public BigDecimal amount;
        public String client = "";
        public String description = "";
        /** Счёт, по которому пришла оплата, или null. */
        public Long invoiceId;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("date", date);
            m.put("amount", amount);
            m.put("client", client);
            m.put("description", description);
            m.put("invoiceId", invoiceId);
            return m;
        }
    }

    public record Item(String name, BigDecimal quantity, BigDecimal price) {
        BigDecimal sum() {
            return quantity.multiply(price);
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

    public static final class Invoice {
        public long id;
        public String number;
        public LocalDate date;
        public LocalDate dueDate;
        public String client;
        public String clientTin = "";
        public InvoiceStatus status = InvoiceStatus.SENT;
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
            m.put("items", items.stream().map(Item::toJson).toList());
            m.put("total", total());
            return m;
        }
    }
}
