package am.pulsar.tax.app.service.session;

import am.pulsar.tax.app.config.SessionProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Сессии в памяти процесса (Caffeine): истекают через TTL после входа. После перезапуска API сессии
 * пропадают и нужно войти заново; для нескольких экземпляров API нужна общая реализация (Redis).
 */
@Component
@ConditionalOnProperty(prefix = "pulsar.session", name = "store", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineSessionStore implements SessionStore {

    private final Cache<String, Long> sessions;

    @Autowired
    public CaffeineSessionStore(SessionProperties props) {
        this(props, Ticker.systemTicker());
    }

    /** Ticker подменяется в тестах, чтобы проверить истечение без ожидания. */
    CaffeineSessionStore(SessionProperties props, Ticker ticker) {
        this.sessions = Caffeine.newBuilder()
                .expireAfterWrite(props.ttl())
                .ticker(ticker)
                .build();
    }

    @Override
    public void save(String tokenHash, Long userId) {
        sessions.put(tokenHash, userId);
    }

    @Override
    public Optional<Long> findUserId(String tokenHash) {
        return Optional.ofNullable(sessions.getIfPresent(tokenHash));
    }

    @Override
    public void delete(String tokenHash) {
        sessions.invalidate(tokenHash);
    }

    @Override
    public void deleteAllForUser(Long userId) {
        sessions.asMap().values().removeIf(id -> Objects.equals(id, userId));
    }
}
