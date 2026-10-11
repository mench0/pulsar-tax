package am.pulsar.tax.dto;import am.pulsar.tax.dto.enums.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
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
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceDto extends BaseDto {

    String number;

    LocalDate date;

    LocalDate dueDate;

    String client;

    String clientTin;

    InvoiceStatus status;

    /** Номер официального электронного счёта (e-invoice.taxservice.am). */
    @JsonProperty("eInvoiceNumber")
    String eInvoiceNumber;

    @Builder.Default
    List<InvoiceItemDto> items = new ArrayList<>();

    /** Сумма счёта в драмах. */
    BigDecimal total;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class InvoiceItemDto {

        String name;

        BigDecimal quantity;

        BigDecimal price;

        /** Сумма позиции, округлённая до драма. */
        BigDecimal sum;
    }
}
