package am.pulsar.tax.invoicing;

import am.pulsar.tax.shared.Enums.InvoiceStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Счёт на оплату. Это не налоговый документ: официальный электронный счёт выписывается
 * в e-invoice.taxservice.am, его номер хранится в eInvoiceNumber.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id")
    private Long businessId;
    private String number;
    private LocalDate date;
    @Column(name = "due_date")
    private LocalDate dueDate;
    private String client;
    @Column(name = "client_tin")
    private String clientTin = "";
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status = InvoiceStatus.SENT;
    @Column(name = "e_invoice_number")
    private String eInvoiceNumber = "";
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_items", joinColumns = @JoinColumn(name = "invoice_id"))
    @OrderColumn(name = "position")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
    }

    public Invoice(Long businessId, String number, LocalDate date, LocalDate dueDate, String client, String clientTin,
                   String eInvoiceNumber, List<InvoiceItem> items) {
        this.businessId = businessId;
        this.number = number;
        this.date = date;
        this.dueDate = dueDate;
        this.client = client;
        this.clientTin = clientTin;
        this.eInvoiceNumber = eInvoiceNumber;
        this.items = new ArrayList<>(items);
    }

    public Long getId() { return id; }
    public Long getBusinessId() { return businessId; }
    public String getNumber() { return number; }
    public LocalDate getDate() { return date; }
    public LocalDate getDueDate() { return dueDate; }
    public String getClient() { return client; }
    public String getClientTin() { return clientTin; }
    public InvoiceStatus getStatus() { return status; }
    public String getEInvoiceNumber() { return eInvoiceNumber; }
    public List<InvoiceItem> getItems() { return items; }

    public void setStatus(InvoiceStatus status) { this.status = status; }
    public void setEInvoiceNumber(String eInvoiceNumber) { this.eInvoiceNumber = eInvoiceNumber; }
    void setNumber(String number) { this.number = number; }

    public BigDecimal total() {
        return items.stream().map(InvoiceItem::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("number", number);
        m.put("date", date);
        m.put("dueDate", dueDate);
        m.put("client", client);
        m.put("clientTin", clientTin);
        m.put("status", status);
        m.put("eInvoiceNumber", eInvoiceNumber);
        m.put("items", items.stream().map(InvoiceItem::toJson).toList());
        m.put("total", total());
        return m;
    }
}
