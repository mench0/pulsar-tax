package am.pulsar.tax.integrations;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Курс ЦБ РА на дату: драмов за 1 единицу валюты. */
@Entity
@Table(name = "fx_rates")
@IdClass(FxRate.Key.class)
public class FxRate {

    public record Key(LocalDate date, String currency) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    private LocalDate date;
    @Id
    private String currency;
    private BigDecimal rate;
    @Column(name = "loaded_at")
    private Instant loadedAt;

    protected FxRate() {
    }

    public FxRate(LocalDate date, String currency, BigDecimal rate, Instant loadedAt) {
        this.date = date;
        this.currency = currency;
        this.rate = rate;
        this.loadedAt = loadedAt;
    }

    public String getCurrency() { return currency; }
    public BigDecimal getRate() { return rate; }
    public Instant getLoadedAt() { return loadedAt; }
}
