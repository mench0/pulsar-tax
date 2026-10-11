package am.pulsar.tax.app.entity;import am.pulsar.tax.dto.enums.InvoiceStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Счёт на оплату. Не налоговый документ: официальный электронный счёт выписывается в e-invoice.taxservice.am,
 * его номер хранится в eInvoiceNumber.
 */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @Column(nullable = false)
    Long businessId;

    @Column(nullable = false)
    String number;

    @Column(nullable = false)
    LocalDate date;

    LocalDate dueDate;

    @Column(nullable = false)
    String client;

    @Column(nullable = false)
    @Builder.Default
    String clientTin = "";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    InvoiceStatus status = InvoiceStatus.SENT;

    @Column(name = "e_invoice_number", nullable = false)
    @Builder.Default
    String eInvoiceNumber = "";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_items", joinColumns = @JoinColumn(name = "invoice_id"))
    @OrderColumn(name = "position")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    List<InvoiceItem> items = new ArrayList<>();

    /** Сумма счёта: сумма позиций, каждая округлена до драма. */
    public BigDecimal total() {
        return items.stream().map(InvoiceItem::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
