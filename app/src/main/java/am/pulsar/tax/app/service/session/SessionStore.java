package am.pulsar.tax.app.service.session;

import java.util.Optional;

/**
 * Хранилище сессий: SHA-256 токена → id пользователя, со временем жизни SessionProperties.ttl.
 * Реализация выбирается настройкой pulsar.session.store: сейчас CaffeineSessionStore (в памяти процесса);
 * для нескольких экземпляров API достаточно добавить реализацию на Redis с тем же контрактом.
 */
public interface SessionStore {

    /** Сохраняет сессию; живёт SessionProperties.ttl с момента сохранения. */
    void save(String tokenHash, Long userId);

    /** Пользователь сессии или пусто, если сессии нет или она истекла. */
    Optional<Long> findUserId(String tokenHash);

    void delete(String tokenHash);

    /** Выход со всех устройств: удаляет все сессии пользователя. */
    void deleteAllForUser(Long userId);
}
