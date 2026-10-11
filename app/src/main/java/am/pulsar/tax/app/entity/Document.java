package am.pulsar.tax.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Загруженный документ. Поля number…counterpartyTin — значения, подтверждённые пользователем;
 * распознанные (неподтверждённые) значения лежат отдельно в extracted (JSON).
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "documents")
public class Document extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    String fileName;

    @Column(nullable = false)
    String contentType;

    @Column(nullable = false)
    Integer sizeBytes;

    @Column(nullable = false)
    String sha256;

    @Column(nullable = false)
    @Builder.Default
    @ToString.Exclude
    String textContent = "";

    @Column(nullable = false)
    String extraction;

    @Column(nullable = false)
    @Builder.Default
    String extracted = "{}";

    @Column(nullable = false)
    @Builder.Default
    String status = "NEEDS_REVIEW";

    @Column(nullable = false)
    @Builder.Default
    String docType = "OTHER";

    @Column(nullable = false)
    @Builder.Default
    String number = "";

    LocalDate docDate;

    String currency;

    BigDecimal amount;

    @Column(nullable = false)
    @Builder.Default
    String counterparty = "";

    @Column(nullable = false)
    @Builder.Default
    String counterpartyTin = "";

    Long expenseId;

    Long incomeId;

    Long invoiceId;

    Long bankTransactionId;
}
