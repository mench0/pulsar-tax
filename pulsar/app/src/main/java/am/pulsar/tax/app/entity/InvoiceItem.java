package am.pulsar.tax.app.entity;import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Позиция счёта. */
@Data
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceItem {

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    BigDecimal quantity;

    @Column(nullable = false)
    BigDecimal price;

    /** Сумма позиции, округлённая до драма. */
    public BigDecimal sum() {
        return quantity.multiply(price).setScale(0, RoundingMode.HALF_UP);
    }
}
