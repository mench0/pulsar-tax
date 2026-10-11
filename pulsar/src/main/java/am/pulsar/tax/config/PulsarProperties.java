package am.pulsar.tax.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Настройки Pulsar Tax (application.yml, переменные окружения PULSAR_*).
 *
 * @param signup         open — регистрация открыта, closed — только первый пользователь
 * @param secureCookie   cookie сессии только по HTTPS
 * @param cookieSameSite Strict — фронтенд на том же сайте, None — на другом домене (нужен HTTPS)
 * @param allowedOrigins адреса фронтенда через запятую (CORS)
 * @param telegramToken  токен бота; пусто — Telegram выключен
 * @param telegramApi    адрес Bot API (в тестах — заглушка)
 * @param cbaUrl         адрес API курсов ЦБ РА
 * @param importLegacy   папка данных старой версии для переноса в базу
 */
@ConfigurationProperties("pulsar")
public record PulsarProperties(
        String signup,
        boolean secureCookie,
        String cookieSameSite,
        String allowedOrigins,
        String telegramToken,
        String telegramApi,
        String cbaUrl,
        String importLegacy) {

    public PulsarProperties {
        if (signup == null || signup.isBlank()) signup = "open";
        if (cookieSameSite == null || cookieSameSite.isBlank()) cookieSameSite = "Strict";
        if (!Set.of("Strict", "Lax", "None").contains(cookieSameSite)) {
            throw new IllegalArgumentException("PULSAR_COOKIE_SAMESITE должен быть Strict, Lax или None: " + cookieSameSite);
        }
        if (cookieSameSite.equals("None") && !secureCookie) {
            throw new IllegalArgumentException("SameSite=None работает только с Secure: включите PULSAR_SECURE_COOKIE=1");
        }
        if (telegramApi == null || telegramApi.isBlank()) telegramApi = "https://api.telegram.org";
        if (cbaUrl == null || cbaUrl.isBlank()) cbaUrl = "https://api.cba.am/exchangerates.asmx";
    }

    public boolean signupOpen() {
        return !"closed".equalsIgnoreCase(signup);
    }

    public boolean telegramEnabled() {
        return telegramToken != null && !telegramToken.isBlank();
    }

    /** Адреса фронтенда без завершающего «/». */
    public Set<String> origins() {
        Set<String> set = new LinkedHashSet<>();
        if (allowedOrigins == null) return set;
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(o -> o.replaceAll("/+$", ""))
                .filter(o -> !o.isEmpty())
                .forEach(set::add);
        return set;
    }
}
