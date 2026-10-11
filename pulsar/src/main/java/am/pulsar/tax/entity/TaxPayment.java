package am.pulsar.tax.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Уплата налога за квартал (у микробизнеса — за год, quarter = 0). */
@Entity
@Table(name = "tax_payments")
public class TaxPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id")
    private Long businessId;
    private int year;
    private int quarter;
    private LocalDate date;
    private BigDecimal amount;

    protected TaxPayment() {
    }

    public TaxPayment(Long businessId, int year, int quarter, LocalDate date, BigDecimal amount) {
        this.businessId = businessId;
        this.year = year;
        this.quarter = quarter;
        this.date = date;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public int getYear() { return year; }
    public int getQuarter() { return quarter; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("year", year);
        m.put("quarter", quarter);
        m.put("date", date);
        m.put("amount", amount);
        return m;
    }
}
