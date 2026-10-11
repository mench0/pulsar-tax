package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.User;
import am.pulsar.tax.app.exception.AuthenticationFailedException;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.repository.UserRepository;
import am.pulsar.tax.app.utils.Passwords;
import am.pulsar.tax.dto.request.AccountUpdateRequestDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Настройки учётной записи, привязка Telegram и календарь по секретной ссылке. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    private static final Duration LINK_CODE_TTL = Duration.ofMinutes(15);

    private record LinkCode(long userId, Instant expires) {
    }

    UserRepository userRepository;
    Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public User getByIdOrElseThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new AuthenticationFailedException("Войдите в Pulsar Tax"));
    }

    @Transactional(readOnly = true)
    public Optional<User> getByCalendarToken(String token) {
        return userRepository.findByCalendarToken(token);
    }

    @Transactional(readOnly = true)
    public List<User> getWithTelegram() {
        return userRepository.findByTelegramChatIdIsNotNull();
    }

    @Transactional
    public User update(Long userId, AccountUpdateRequestDto request) {
        if (request.getLang() == null || !AuthService.LANGUAGES.contains(request.getLang())) {
            throw new CustomValidationException("Неизвестный язык: " + request.getLang());
        }
        User user = getByIdOrElseThrow(userId);
        user.setLang(request.getLang());
        return userRepository.saveAndFlush(user);
    }

    /** Код для привязки Telegram: пользователь отправляет боту /start КОД. */
    public String telegramCode(Long userId) {
        String code = Passwords.randomHex(8);
        Instant now = Instant.now();
        linkCodes.entrySet().removeIf(e -> e.getValue().expires().isBefore(now));
        linkCodes.put(code, new LinkCode(userId, now.plus(LINK_CODE_TTL)));
        return code;
    }

    /** Привязывает чат по коду. Пусто, если код неверный или устарел. */
    @Transactional
    public Optional<User> linkTelegram(String code, long chatId) {
        LinkCode c = code == null ? null : linkCodes.remove(code);
        if (c == null || c.expires().isBefore(Instant.now())) {
            return Optional.empty();
        }
        Optional<User> user = userRepository.findById(c.userId());
        user.ifPresent(u -> u.setTelegramChatId(chatId));
        return user;
    }

    @Transactional
    public User unlinkTelegram(Long userId) {
        User user = getByIdOrElseThrow(userId);
        user.setTelegramChatId(null);
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public void markReminded(Long userId, LocalDate day) {
        getByIdOrElseThrow(userId).setLastReminder(day);
    }
}
