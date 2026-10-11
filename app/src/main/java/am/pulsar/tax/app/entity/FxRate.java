package am.pulsar.tax.app.entity;import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Курс ЦБ РА на дату: драмов за 1 единицу валюты (кэш). */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@IdClass(FxRate.Key.class)
@Table(name = "fx_rates")
public class FxRate {

    @Id
    LocalDate date;

    @Id
    String currency;

    @Column(nullable = false)
    BigDecimal rate;

    @Column(nullable = false)
    Instant loadedAt;

    public record Key(LocalDate date, String currency) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
