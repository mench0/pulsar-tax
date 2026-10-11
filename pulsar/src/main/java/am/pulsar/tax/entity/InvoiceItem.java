package am.pulsar.tax.entity;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Embeddable
public class InvoiceItem {

    private String name;
    private BigDecimal quantity;
    private BigDecimal price;

    protected InvoiceItem() {
    }

    public InvoiceItem(String name, BigDecimal quantity, BigDecimal price) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    public String getName() { return name; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }

    /** Сумма позиции, округлённая до драма. */
    public BigDecimal sum() {
        return quantity.multiply(price).setScale(0, RoundingMode.HALF_UP);
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("quantity", quantity);
        m.put("price", price);
        m.put("sum", sum());
        return m;
    }
}
