package am.pulsar.tax.accounting;

import am.pulsar.tax.shared.Enums.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Поступление. Налог и порог считаются от суммы в драмах (amount). */
@Entity
@Table(name = "incomes")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id")
    private Long businessId;
    private LocalDate date;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private Currency currency = Currency.AMD;
    /** Сумма в валюте и курс к драму; null у поступлений в драмах. */
    @Column(name = "original_amount")
    private BigDecimal originalAmount;
    private BigDecimal rate;
    private String client = "";
    private String description = "";
    /** Счёт, по которому пришла оплата. */
    @Column(name = "invoice_id")
    private Long invoiceId;
    /** Отпечаток строки выписки; null у ручных записей. */
    @Column(name = "import_key")
    private String importKey;

    protected Income() {
    }

    public Income(Long businessId, LocalDate date, BigDecimal amount, Currency currency, BigDecimal originalAmount,
                  BigDecimal rate, String client, String description, Long invoiceId, String importKey) {
        this.businessId = businessId;
        this.date = date;
        this.amount = amount;
        this.currency = currency;
        this.originalAmount = originalAmount;
        this.rate = rate;
        this.client = client;
        this.description = description;
        this.invoiceId = invoiceId;
        this.importKey = importKey;
    }

    public Long getId() { return id; }
    public Long getBusinessId() { return businessId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public BigDecimal getRate() { return rate; }
    public String getClient() { return client; }
    public String getDescription() { return description; }
    public Long getInvoiceId() { return invoiceId; }
    public String getImportKey() { return importKey; }

    public Map<String, Object> toJson() {
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
