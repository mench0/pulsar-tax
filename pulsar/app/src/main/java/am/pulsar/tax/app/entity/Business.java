package am.pulsar.tax.app.entity;import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.TaxRegime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/** Индивидуальный предприниматель (профиль). Пока один на пользователя. */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "businesses")
public class Business extends BaseEntity {

    @Column(nullable = false, unique = true)
    Long ownerId;

    @Column(nullable = false)
    @Builder.Default
    String name = "";

    @Column(nullable = false)
    @Builder.Default
    String tin = "";

    @Column(nullable = false)
    @Builder.Default
    String address = "";

    @Column(nullable = false)
    @Builder.Default
    String bankAccount = "";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    TaxRegime regime = TaxRegime.MICRO;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    ActivityType activity = ActivityType.SERVICES;

    /** Ставка в процентах для ActivityType.CUSTOM. */
    BigDecimal customRate;
}
