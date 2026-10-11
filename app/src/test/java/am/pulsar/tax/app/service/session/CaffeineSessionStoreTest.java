package am.pulsar.tax.app.service.session;

import am.pulsar.tax.app.config.SessionProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Сессии в Caffeine: время жизни, выход и выход со всех устройств. Время подменяется, ждать не нужно. */
class CaffeineSessionStoreTest {

    final AtomicLong nanos = new AtomicLong();
    final CaffeineSessionStore store = new CaffeineSessionStore(new SessionProperties(Duration.ofHours(1), "caffeine"), nanos::get);

    @Test
    void sessionExpiresAfterTtl() {
        store.save("a", 1L);
        nanos.addAndGet(Duration.ofMinutes(59).toNanos());
        assertEquals(Optional.of(1L), store.findUserId("a"));
        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        assertEquals(Optional.empty(), store.findUserId("a"));
    }

    @Test
    void deleteAndDeleteAllForUser() {
        store.save("a", 1L);
        store.save("b", 1L);
        store.save("c", 2L);
        store.delete("a");
        assertEquals(Optional.empty(), store.findUserId("a"));
        store.deleteAllForUser(1L);
        assertEquals(Optional.empty(), store.findUserId("b"));
        assertEquals(Optional.of(2L), store.findUserId("c"));
    }
}
