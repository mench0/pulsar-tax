package am.pulsar.tax.controller;

import am.pulsar.tax.client.TelegramBot;
import am.pulsar.tax.config.PulsarProperties;
import am.pulsar.tax.entity.User;
import am.pulsar.tax.exception.ApiException;
import am.pulsar.tax.security.AuthUser;
import am.pulsar.tax.security.SessionCookies;
import am.pulsar.tax.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static am.pulsar.tax.util.Requests.str;

/**
 * Вход и учётная запись. Сессию браузер получает в cookie HttpOnly; мобильное приложение передаёт
 * "tokenAuth": true, получает token в ответе и шлёт его в Authorization: Bearer.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AccountService accounts;
    private final SessionCookies cookies;
    private final TelegramBot telegram;
    private final PulsarProperties props;

    public AuthController(AccountService accounts, SessionCookies cookies, TelegramBot telegram, PulsarProperties props) {
        this.accounts = accounts;
        this.cookies = cookies;
        this.telegram = telegram;
        this.props = props;
    }

    @PostMapping("/auth/register")
    ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> b, HttpServletResponse res) {
        AccountService.Login login = accounts.register(str(b.get("email")), str(b.get("password")), str(b.get("lang")));
        return ResponseEntity.status(201).body(issue(res, tokenAuth(b), login));
    }

    @PostMapping("/auth/login")
    Map<String, Object> login(@RequestBody Map<String, Object> b, HttpServletResponse res) {
        return issue(res, tokenAuth(b), accounts.login(str(b.get("email")), str(b.get("password"))));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        accounts.logout(SessionCookies.token(req));
        cookies.clear(res);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/me")
    Map<String, Object> me(@AuthenticationPrincipal AuthUser user) {
        return me(accounts.get(user.id()));
    }

    @PutMapping("/account")
    Map<String, Object> account(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> b) {
        return me(accounts.setLang(user.id(), str(b.get("lang"))));
    }

    @PostMapping("/account/password")
    Map<String, Object> password(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> b,
                                 HttpServletResponse res) {
        AccountService.Login login = accounts.changePassword(user.id(), str(b.get("oldPassword")), str(b.get("newPassword")));
        return issue(res, user.bearer(), login);
    }

    @PostMapping("/account/telegram")
    Map<String, Object> linkTelegram(@AuthenticationPrincipal AuthUser user) {
        if (!telegram.enabled()) throw ApiException.badRequest("Telegram на этом сервере не настроен");
        if (telegram.username() == null) throw new ApiException(503, "Бот Telegram ещё запускается, попробуйте через минуту");
        String code = accounts.telegramCode(user.id());
        return Map.of("code", code, "link", "https://t.me/" + telegram.username() + "?start=" + code);
    }

    @DeleteMapping("/account/telegram")
    Map<String, Object> unlinkTelegram(@AuthenticationPrincipal AuthUser user) {
        return me(accounts.unlinkTelegram(user.id()));
    }

    private Map<String, Object> issue(HttpServletResponse res, boolean tokenAuth, AccountService.Login login) {
        Map<String, Object> m = me(login.user());
        if (tokenAuth) {
            m.put("token", login.token());
        } else {
            cookies.set(res, login.token());
        }
        return m;
    }

    private Map<String, Object> me(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.getEmail());
        m.put("lang", u.getLang());
        m.put("telegramEnabled", props.telegramEnabled());
        m.put("telegramLinked", u.getTelegramChatId() != null);
        m.put("calendarPath", "/cal/" + u.getCalendarToken() + ".ics");
        return m;
    }

    private static boolean tokenAuth(Map<String, Object> body) {
        return Boolean.TRUE.equals(body.get("tokenAuth"));
    }
}
