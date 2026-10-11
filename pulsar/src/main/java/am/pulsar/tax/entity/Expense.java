package am.pulsar.tax.entity;

import am.pulsar.tax.entity.Enums.ExpenseCategory;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Расход. Пока только учёт и сводка: на налог не влияет, пока правила вычета расходов
 * из налога с оборота не сверены с Налоговым кодексом.
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id")
    private Long businessId;
    private LocalDate date;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private ExpenseCategory category = ExpenseCategory.OTHER;
    private String description = "";

    protected Expense() {
    }

    public Expense(Long businessId, LocalDate date, BigDecimal amount, ExpenseCategory category, String description) {
        this.businessId = businessId;
        this.date = date;
        this.amount = amount;
        this.category = category;
        this.description = description;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public ExpenseCategory getCategory() { return category; }
    public String getDescription() { return description; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("date", date);
        m.put("amount", amount);
        m.put("category", category);
        m.put("description", description);
        return m;
    }
}
