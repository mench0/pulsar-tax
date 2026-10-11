package am.pulsar.tax.app.entity;import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Сессия. Хранится только SHA-256 токена: утечка базы не даёт войти. */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "sessions")
public class Session {

    @Id
    String tokenHash;

    @Column(nullable = false)
    Long userId;

    @Column(nullable = false)
    Instant expiresAt;

    @Column(insertable = false, updatable = false)
    Instant createdAt;
}
