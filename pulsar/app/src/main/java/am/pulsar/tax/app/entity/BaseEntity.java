package am.pulsar.tax.app.entity;import am.pulsar.tax.app.utils.ClockUtils;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    Long id;

    @Column(nullable = false)
    OffsetDateTime createdAt;

    @Column(nullable = false)
    OffsetDateTime lastUpdatedAt;

    @PrePersist
    protected void prePersist() {
        createdAt = ClockUtils.now();
        lastUpdatedAt = createdAt;
    }

    @PreUpdate
    protected void preUpdate() {
        lastUpdatedAt = ClockUtils.now();
    }
}
