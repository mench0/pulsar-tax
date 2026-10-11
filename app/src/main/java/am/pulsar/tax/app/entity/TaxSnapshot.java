package am.pulsar.tax.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Сохранённый налоговый расчёт за квартал или год (quarter = null): с ним сравнивается текущий. */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "tax_snapshots")
public class TaxSnapshot extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    Integer year;

    Integer quarter;

    @Column(nullable = false)
    @Builder.Default
    String reason = "";

    BigDecimal accrued;

    /** JSON расчёта (TaxEngine.Calculation). */
    @Column(nullable = false)
    String payload;
}
