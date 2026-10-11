package am.pulsar.tax.security;

import am.pulsar.tax.config.PulsarProperties;
import am.pulsar.tax.service.AccountService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Где лежит токен сессии. Браузер — cookie HttpOnly, чтобы скрипт страницы не мог её прочитать.
 * Мобильное приложение — заголовок Authorization: Bearer.
 */
@Component
public class SessionCookies {

    public static final String COOKIE = "pulsar_session";

    private final PulsarProperties props;

    public SessionCookies(PulsarProperties props) {
        this.props = props;
    }

    public static String bearer(HttpServletRequest req) {
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        return h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }

    public static String token(HttpServletRequest req) {
        String b = bearer(req);
        if (b != null) return b;
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    public void set(HttpServletResponse res, String token) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(token, AccountService.SESSION_TTL.toSeconds()));
    }

    public void clear(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0));
    }

    private String cookie(String value, long maxAge) {
        return COOKIE + "=" + value + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=" + props.cookieSameSite()
                + (props.secureCookie() ? "; Secure" : "");
    }
}
