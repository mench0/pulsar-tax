package am.pulsar.tax.app.entity;import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Уплата налога за квартал (у микробизнеса — за год, quarter = 0). */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "tax_payments")
public class TaxPayment extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    Integer year;

    @Column(nullable = false)
    Integer quarter;

    @Column(nullable = false)
    LocalDate date;

    @Column(nullable = false)
    BigDecimal amount;
}
