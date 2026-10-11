package am.pulsar.tax.dto.create;import am.pulsar.tax.dto.InvoiceDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Счёт на оплату. Без номера получает следующий вида PT-2026-001. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
public class InvoiceCreateDto implements BaseCreateDto<InvoiceDto> {

    @Size(max = 50, message = "Поле «number» длиннее 50 символов")
    String number;

    /** Дата счёта; по умолчанию сегодня. */
    LocalDate date;

    LocalDate dueDate;

    @NotBlank(message = "Заполните поле «client»")
    @Size(max = 200, message = "Поле «client» длиннее 200 символов")
    String client;

    @Size(max = 20, message = "Поле «clientTin» длиннее 20 символов")
    String clientTin;

    @JsonProperty("eInvoiceNumber")
    @Size(max = 50, message = "Поле «eInvoiceNumber» длиннее 50 символов")
    String eInvoiceNumber;

    @NotEmpty(message = "Добавьте хотя бы одну позицию в счёт")
    @Size(max = 200, message = "В счёте не больше 200 позиций")
    List<@NotNull(message = "Неверная позиция счёта") @Valid InvoiceItemInputDto> items;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class InvoiceItemInputDto {

        @NotBlank(message = "Заполните поле «name»")
        @Size(max = 300, message = "Поле «name» длиннее 300 символов")
        String name;

        @NotNull(message = "Заполните поле «quantity»")
        @Positive(message = "Количество должно быть больше нуля")
        BigDecimal quantity;

        @NotNull(message = "Заполните поле «price»")
        @PositiveOrZero(message = "Цена не может быть отрицательной")
        BigDecimal price;
    }
}
