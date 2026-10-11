package am.pulsar.tax.identity;

import jakarta.persistence.*;

import java.time.Instant;

/** Сессия. Хранится только SHA-256 токена: утечка базы не даёт войти. */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Session() {
    }

    public Session(String tokenHash, Long userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
