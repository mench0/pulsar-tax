package am.pulsar.tax.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Сессии (application.yml, pulsar.session.*).
 *
 * @param ttl   время жизни сессии от входа (PULSAR_SESSION_TTL), по умолчанию 1 час
 * @param store хранилище: caffeine — в памяти процесса (по умолчанию); redis — общее для нескольких экземпляров
 */
@ConfigurationProperties("pulsar.session")
public record SessionProperties(Duration ttl, String store) {

    public SessionProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("pulsar.session.ttl должен быть больше нуля: " + ttl);
        }
        if (store == null || store.isBlank()) {
            store = "caffeine";
        }
    }
}
