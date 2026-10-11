package am.pulsar.tax.app.service;import am.pulsar.tax.app.config.PulsarProperties;
import am.pulsar.tax.app.entity.Session;
import am.pulsar.tax.app.entity.User;
import am.pulsar.tax.app.exception.AuthenticationFailedException;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.DuplicateException;
import am.pulsar.tax.app.exception.NotAvailableOperationException;
import am.pulsar.tax.app.exception.TooManyAttemptsException;
import am.pulsar.tax.app.repository.SessionRepository;
import am.pulsar.tax.app.repository.UserRepository;
import am.pulsar.tax.app.utils.Passwords;
import am.pulsar.tax.dto.auth.LoginRequestDto;
import am.pulsar.tax.dto.auth.RegisterRequestDto;
import am.pulsar.tax.dto.request.PasswordChangeRequestDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Регистрация, вход и сессии. Пароли — PBKDF2-SHA256 с солью; токен сессии уходит клиенту, в базе — только
 * его SHA-256. После 5 неверных паролей вход по этой почте блокируется на 15 минут.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    public static final List<String> LANGUAGES = List.of("ru", "en", "hy");
    public static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$");

    /** Новая сессия: пользователь и токен, который уходит клиенту. */
    public record Login(User user, String token) {
    }

    private record Failures(int count, Instant lockedUntil) {
    }

    UserRepository userRepository;
    SessionRepository sessionRepository;
    PulsarProperties props;
    Map<String, Failures> failures = new ConcurrentHashMap<>();

    /** Регистрация; при закрытой регистрации (PULSAR_SIGNUP=closed) можно создать только первого пользователя. */
    @Transactional
    public Login register(RegisterRequestDto request) {
        if (!props.signupOpen() && userRepository.count() > 0) {
            throw new NotAvailableOperationException("Регистрация закрыта");
        }
        String email = normalize(request.getEmail());
        if (!EMAIL.matcher(email).matches()) {
            throw new CustomValidationException("Неверный адрес почты");
        }
        checkPassword(request.getPassword());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateException("Пользователь с такой почтой уже есть");
        }
        String salt = Passwords.randomHex(16);
        User user = User.builder()
                .email(email)
                .passwordSalt(salt)
                .passwordHash(Passwords.hash(request.getPassword(), salt))
                .calendarToken(Passwords.randomHex(20))
                .lang(request.getLang() != null && LANGUAGES.contains(request.getLang()) ? request.getLang() : "ru")
                .build();
        userRepository.save(user);
        return new Login(user, newSession(user));
    }

    @Transactional
    public Login login(LoginRequestDto request) {
        String email = normalize(request.getEmail());
        Instant now = Instant.now();
        Failures f = failures.get(email);
        if (f != null && f.lockedUntil() != null && f.lockedUntil().isAfter(now)) {
            throw new TooManyAttemptsException();
        }
        Optional<User> found = userRepository.findByEmail(email);
        String password = request.getPassword() == null ? "" : request.getPassword();
        boolean ok;
        if (found.isPresent()) {
            ok = Passwords.matches(password, found.get().getPasswordSalt(), found.get().getPasswordHash());
        } else {
            // Хэш считаем и для несуществующей почты, чтобы по времени ответа нельзя было узнать, есть ли она.
            Passwords.hash(password, "00");
            ok = false;
        }
        if (!ok) {
            failures.compute(email, (k, old) -> {
                int count = old == null ? 1 : old.count() + 1;
                return new Failures(count, count >= MAX_FAILURES ? now.plus(LOCK) : null);
            });
            throw new AuthenticationFailedException("Неверная почта или пароль");
        }
        failures.remove(email);
        return new Login(found.get(), newSession(found.get()));
    }

    @Transactional
    public void logout(String token) {
        if (token != null && token.length() <= 100) {
            sessionRepository.deleteById(Passwords.sha256(token));
        }
    }

    /** Пользователь по токену сессии. */
    @Transactional(readOnly = true)
    public Optional<Long> getUserIdBySession(String token) {
        if (token == null || token.isEmpty() || token.length() > 100) {
            return Optional.empty();
        }
        return sessionRepository.findById(Passwords.sha256(token))
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(Session::getUserId);
    }

    /** Меняет пароль, выходит со всех устройств и выдаёт новую сессию текущему. */
    @Transactional
    public Login changePassword(Long userId, PasswordChangeRequestDto request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationFailedException("Войдите в Pulsar Tax"));
        String oldPassword = request.getOldPassword() == null ? "" : request.getOldPassword();
        if (!Passwords.matches(oldPassword, user.getPasswordSalt(), user.getPasswordHash())) {
            throw new CustomValidationException("Текущий пароль указан неверно");
        }
        checkPassword(request.getNewPassword());
        String salt = Passwords.randomHex(16);
        user.setPasswordSalt(salt);
        user.setPasswordHash(Passwords.hash(request.getNewPassword(), salt));
        sessionRepository.deleteByUser(user.getId());
        return new Login(user, newSession(user));
    }

    private String newSession(User user) {
        String token = Passwords.randomHex(32);
        sessionRepository.save(Session.builder()
                .tokenHash(Passwords.sha256(token))
                .userId(user.getId())
                .expiresAt(Instant.now().plus(SESSION_TTL))
                .build());
        return token;
    }

    /** Раз в час удаляет просроченные сессии. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void purgeSessions() {
        sessionRepository.deleteExpired(Instant.now());
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new CustomValidationException("Пароль должен быть не короче 8 символов");
        }
        if (password.length() > 200) {
            throw new CustomValidationException("Слишком длинный пароль");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
