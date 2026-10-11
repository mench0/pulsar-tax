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
import java.time.LocalDate;

/**
 * Подготовленный отчёт: зафиксированные цифры (снимок расчёта) и отметка пользователя о подаче.
 * Статусы: PREPARED — готов к подаче, FILED_MANUALLY — пользователь сообщил, что подал сам,
 * SUPERSEDED — заменён более новой версией.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "tax_reports")
public class TaxReport extends BaseEntity {

    public static final String PREPARED = "PREPARED";
    public static final String FILED_MANUALLY = "FILED_MANUALLY";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    String type;

    @Column(nullable = false)
    Integer year;

    Integer quarter;

    @Column(nullable = false)
    @Builder.Default
    Integer version = 1;

    @Column(nullable = false)
    @Builder.Default
    String status = PREPARED;

    @Column(nullable = false)
    Long snapshotId;

    @Column(nullable = false)
    BigDecimal turnover;

    BigDecimal accrued;

    @Column(nullable = false)
    BigDecimal paid;

    BigDecimal due;

    @Column(nullable = false)
    @Builder.Default
    String warnings = "[]";

    LocalDate filedOn;

    @Column(nullable = false)
    @Builder.Default
    String filingReference = "";

    @Column(nullable = false)
    @Builder.Default
    String filingNote = "";
}
