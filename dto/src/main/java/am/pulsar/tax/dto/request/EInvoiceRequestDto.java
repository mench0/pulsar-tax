package am.pulsar.tax.dto.request;import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Номер официального электронного счёта из e-invoice.taxservice.am. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EInvoiceRequestDto {

    @JsonProperty("eInvoiceNumber")
    @Size(max = 50, message = "Поле «eInvoiceNumber» длиннее 50 символов")
    String eInvoiceNumber;
}
