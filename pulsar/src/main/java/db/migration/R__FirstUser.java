package db.migration;

import am.pulsar.tax.security.Passwords;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;

/**
 * Первый пользователь из переменных окружения PULSAR_FIRST_USER_EMAIL и PULSAR_FIRST_USER_PASSWORD.
 * Нужен, например, когда регистрация закрыта (PULSAR_SIGNUP=closed).
 *
 * Миграция повторяемая: Flyway запускает её снова, когда меняется почта. Существующего пользователя
 * она не трогает и пароль не сбрасывает. Пароль в репозитории не хранится — только хэш PBKDF2 в базе.
 */
public class R__FirstUser extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return email(System.getenv()).hashCode();
    }

    @Override
    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String email = email(placeholders);
        if (email.isEmpty()) return;
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalStateException("PULSAR_FIRST_USER_EMAIL: неверный адрес почты");
        }
        String password = placeholders.getOrDefault("first_user_password", "");
        if (password.length() < 8) {
            throw new IllegalStateException("PULSAR_FIRST_USER_PASSWORD: пароль первого пользователя — не короче 8 символов");
        }
        Connection c = context.getConnection();
        try (PreparedStatement exists = c.prepareStatement("select 1 from users where email = ?")) {
            exists.setString(1, email);
            try (ResultSet r = exists.executeQuery()) {
                if (r.next()) return;
            }
        }
        String salt = Passwords.randomHex(16);
        long id;
        try (PreparedStatement insert = c.prepareStatement(
                "insert into users (email, password_salt, password_hash, calendar_token) values (?, ?, ?, ?) returning id")) {
            insert.setString(1, email);
            insert.setString(2, salt);
            insert.setString(3, Passwords.hash(password, salt));
            insert.setString(4, Passwords.randomHex(20));
            try (ResultSet r = insert.executeQuery()) {
                r.next();
                id = r.getLong(1);
            }
        }
        try (PreparedStatement business = c.prepareStatement("insert into businesses (owner_id) values (?)")) {
            business.setLong(1, id);
            business.executeUpdate();
        }
    }

    /** Почта из плейсхолдера Flyway или из окружения (для контрольной суммы). */
    private static String email(Map<String, String> source) {
        String v = source.getOrDefault("first_user_email", source.getOrDefault("PULSAR_FIRST_USER_EMAIL", ""));
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }
}
