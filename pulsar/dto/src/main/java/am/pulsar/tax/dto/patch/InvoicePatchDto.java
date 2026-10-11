package am.pulsar.tax.dto.patch;import am.pulsar.tax.dto.InvoiceDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.Optional;

/** Изменение счёта: реквизиты и номер e-invoice. Позиции и статус меняются отдельно. */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class InvoicePatchDto extends BasePatchDto<InvoiceDto> {

    Optional<LocalDate> dueDate;

    Optional<@Size(max = 200, message = "Поле «client» длиннее 200 символов") String> client;

    Optional<@Size(max = 20, message = "Поле «clientTin» длиннее 20 символов") String> clientTin;

    @JsonProperty("eInvoiceNumber")
    Optional<@Size(max = 50, message = "Поле «eInvoiceNumber» длиннее 50 символов") String> eInvoiceNumber;
}
