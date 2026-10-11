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

/** Часть дохода, закрывающая счёт, или возврат покупателю по счёту (bankTransactionId, сумма отрицательная). */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "invoice_payments")
public class InvoicePayment extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    Long invoiceId;

    Long incomeId;

    Long bankTransactionId;

    @Column(nullable = false)
    BigDecimal amount;

    @Column(nullable = false)
    @Builder.Default
    String source = "MANUAL";
}
