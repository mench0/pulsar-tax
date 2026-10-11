package am.pulsar.tax.identity;

import am.pulsar.tax.shared.ApiException;
import am.pulsar.tax.shared.PulsarProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Регистрация, вход, сессии, настройки учётной записи и привязка Telegram. */
@Service
public class AccountService {

    public static final List<String> LANGUAGES = List.of("ru", "en", "hy");
    public static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Duration LINK_CODE_TTL = Duration.ofMinutes(15);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$");

    /** Новая сессия: пользователь и токен, который уходит клиенту (в базе только его хэш). */
    public record Login(User user, String token) {
    }

    private record Failures(int count, Instant lockedUntil) {
    }

    private record LinkCode(long userId, Instant expires) {
    }

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PulsarProperties props;
    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();

    public AccountService(UserRepository users, SessionRepository sessions, PulsarProperties props) {
        this.users = users;
        this.sessions = sessions;
        this.props = props;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public Login register(String email, String password, String lang) {
        if (!props.signupOpen() && users.count() > 0) throw new ApiException(403, "Регистрация закрыта");
        String e = normalize(email);
        if (!EMAIL.matcher(e).matches()) throw ApiException.badRequest("Неверный адрес почты");
        checkPassword(password);
        if (users.findByEmail(e).isPresent()) throw new ApiException(409, "Пользователь с такой почтой уже есть");
        String salt = Passwords.randomHex(16);
        User u = new User(e, salt, Passwords.hash(password, salt), Passwords.randomHex(20));
        if (lang != null && LANGUAGES.contains(lang)) u.setLang(lang);
        users.save(u);
        return new Login(u, newSession(u));
    }

    @Transactional
    public Login login(String email, String password) {
        String e = normalize(email);
        Instant now = clock.instant();
        Failures f = failures.get(e);
        if (f != null && f.lockedUntil != null && f.lockedUntil.isAfter(now)) {
            throw new ApiException(429, "Слишком много попыток. Попробуйте через 15 минут");
        }
        Optional<User> found = users.findByEmail(e);
        // Хэш считаем и для несуществующей почты, чтобы по времени ответа нельзя было узнать, есть ли она.
        String pass = password == null ? "" : password;
        boolean ok;
        if (found.isPresent()) {
            ok = Passwords.matches(pass, found.get().getPasswordSalt(), found.get().getPasswordHash());
        } else {
            Passwords.hash(pass, "00");
            ok = false;
        }
        if (!ok) {
            failures.compute(e, (k, old) -> {
                int count = old == null ? 1 : old.count + 1;
                return new Failures(count, count >= MAX_FAILURES ? now.plus(LOCK) : null);
            });
            throw new ApiException(401, "Неверная почта или пароль");
        }
        failures.remove(e);
        return new Login(found.get(), newSession(found.get()));
    }

    @Transactional
    public String newSession(User u) {
        String token = Passwords.randomHex(32);
        sessions.save(new Session(Passwords.sha256(token), u.getId(), clock.instant().plus(SESSION_TTL)));
        return token;
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.length() > 100) return;
        sessions.deleteById(Passwords.sha256(token));
    }

    /** Пользователь по токену сессии. */
    @Transactional(readOnly = true)
    public Optional<Long> userBySession(String token) {
        if (token == null || token.isEmpty() || token.length() > 100) return Optional.empty();
        return sessions.findById(Passwords.sha256(token))
                .filter(s -> s.getExpiresAt().isAfter(clock.instant()))
                .map(Session::getUserId);
    }

    @Transactional(readOnly = true)
    public User get(long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(401, "Войдите в Pulsar Tax"));
    }

    @Transactional(readOnly = true)
    public Optional<User> byCalendarToken(String token) {
        return users.findByCalendarToken(token);
    }

    /** Меняет пароль, выходит со всех устройств и выдаёт новую сессию текущему. */
    @Transactional
    public Login changePassword(long userId, String oldPassword, String newPassword) {
        User u = get(userId);
        if (!Passwords.matches(oldPassword == null ? "" : oldPassword, u.getPasswordSalt(), u.getPasswordHash())) {
            throw ApiException.badRequest("Текущий пароль указан неверно");
        }
        checkPassword(newPassword);
        String salt = Passwords.randomHex(16);
        u.setPassword(salt, Passwords.hash(newPassword, salt));
        sessions.deleteByUser(u.getId());
        return new Login(u, newSession(u));
    }

    @Transactional
    public User setLang(long userId, String lang) {
        if (lang == null || !LANGUAGES.contains(lang)) throw ApiException.badRequest("Неизвестный язык: " + lang);
        User u = get(userId);
        u.setLang(lang);
        return u;
    }

    /** Код для привязки Telegram: пользователь отправляет боту /start КОД. */
    public String telegramCode(long userId) {
        String code = Passwords.randomHex(8);
        Instant now = clock.instant();
        linkCodes.entrySet().removeIf(e -> e.getValue().expires.isBefore(now));
        linkCodes.put(code, new LinkCode(userId, now.plus(LINK_CODE_TTL)));
        return code;
    }

    /** Привязывает чат по коду. Пусто, если код неверный или устарел. */
    @Transactional
    public Optional<User> linkTelegram(String code, long chatId) {
        LinkCode c = code == null ? null : linkCodes.remove(code);
        if (c == null || c.expires.isBefore(clock.instant())) return Optional.empty();
        Optional<User> u = users.findById(c.userId);
        u.ifPresent(user -> user.setTelegramChatId(chatId));
        return u;
    }

    @Transactional
    public User unlinkTelegram(long userId) {
        User u = get(userId);
        u.setTelegramChatId(null);
        return u;
    }

    @Transactional
    public void markReminded(long userId, LocalDate day) {
        get(userId).setLastReminder(day);
    }

    /** Раз в час удаляет просроченные сессии. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void purgeSessions() {
        sessions.deleteExpired(clock.instant());
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8) throw ApiException.badRequest("Пароль должен быть не короче 8 символов");
        if (password.length() > 200) throw ApiException.badRequest("Слишком длинный пароль");
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
