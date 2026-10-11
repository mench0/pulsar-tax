package am.pulsar.tax.app.entity;import am.pulsar.tax.dto.enums.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Расход. Пока только учёт и сводка: на налог не влияет. */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    LocalDate date;

    @Column(nullable = false)
    BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    ExpenseCategory category = ExpenseCategory.OTHER;

    @Column(nullable = false)
    @Builder.Default
    String description = "";
}
