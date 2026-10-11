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
import lombok.experimental.FieldNameConstants;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** Сопоставление колонок банковской выписки по отпечатку её заголовка; mapping — JSON. */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@IdClass(ImportProfile.Key.class)
@Table(name = "import_profiles")
public class ImportProfile {

    @Id
    Long businessId;

    @Id
    String signature;

    @Column(nullable = false)
    String mapping;

    @Column(nullable = false)
    OffsetDateTime updatedAt;

    public record Key(Long businessId, String signature) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
