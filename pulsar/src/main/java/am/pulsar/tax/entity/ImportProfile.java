package am.pulsar.tax.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;

/** Сопоставление колонок выписки банка по отпечатку её заголовка (JSON). */
@Entity
@Table(name = "import_profiles")
@IdClass(ImportProfile.Key.class)
public class ImportProfile {

    public record Key(Long businessId, String signature) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "business_id")
    private Long businessId;
    @Id
    private String signature;
    private String mapping;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ImportProfile() {
    }

    public ImportProfile(Long businessId, String signature) {
        this.businessId = businessId;
        this.signature = signature;
    }

    public String getSignature() { return signature; }
    public String getMapping() { return mapping; }

    public void setMapping(String mapping, Instant now) {
        this.mapping = mapping;
        this.updatedAt = now;
    }
}
