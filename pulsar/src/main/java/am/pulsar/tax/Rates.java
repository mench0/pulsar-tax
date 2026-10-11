package am.pulsar.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Официальные курсы ЦБ РА (api.cba.am, SOAP-метод ExchangeRatesByDate) — драмов за единицу валюты.
 * Курсы за прошедшие даты не меняются, поэтому кэшируются навсегда; за сегодня — на час.
 */
final class Rates {

    static final String DEFAULT_BASE = "https://api.cba.am/exchangerates.asmx";
    private static final Pattern RATE = Pattern.compile(
            "<ExchangeRate>.*?<ISO>(\\w{3})</ISO>.*?<Amount>([\\d.]+)</Amount>.*?<Rate>([\\d.]+)</Rate>.*?</ExchangeRate>",
            Pattern.DOTALL);
    private static final long TODAY_TTL_MS = 60 * 60 * 1000;

    private record Cached(Map<String, BigDecimal> rates, long loadedAt) {
    }

    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<LocalDate, Cached> cache = new ConcurrentHashMap<>();

    Rates(String url) {
        this.url = url;
    }

    /** Курсы на дату: ISO-код → драмов за 1 единицу. Валюты, которых нет в Pulsar, тоже отдаются. */
    Map<String, BigDecimal> on(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) throw new ApiException(400, "Курс на будущую дату ещё не известен");
        Cached c = cache.get(date);
        boolean fresh = c != null && (date.isBefore(today) || System.currentTimeMillis() - c.loadedAt < TODAY_TTL_MS);
        if (fresh) return c.rates;
        Map<String, BigDecimal> rates = fetch(date);
        cache.put(date, new Cached(rates, System.currentTimeMillis()));
        return rates;
    }

    private Map<String, BigDecimal> fetch(LocalDate date) {
        String body = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <ExchangeRatesByDate xmlns="http://www.cba.am/"><date>%sT00:00:00</date></ExchangeRatesByDate>
                  </soap:Body>
                </soap:Envelope>""".formatted(date);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"http://www.cba.am/ExchangeRatesByDate\"")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        String xml;
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new ApiException(503, "ЦБ РА не ответил (код " + res.statusCode() + ")");
            xml = res.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(503, "Не удалось получить курс ЦБ РА. Укажите курс вручную");
        }
        return parse(xml);
    }

    /** Разбор ответа ЦБ: курс делится на Amount (у некоторых валют курс дан за 10 или 100 единиц). */
    static Map<String, BigDecimal> parse(String xml) {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        Matcher m = RATE.matcher(xml);
        while (m.find()) {
            BigDecimal amount = new BigDecimal(m.group(2));
            if (amount.signum() <= 0) continue;
            rates.put(m.group(1), new BigDecimal(m.group(3)).divide(amount, 6, RoundingMode.HALF_UP).stripTrailingZeros());
        }
        if (rates.isEmpty()) throw new ApiException(503, "ЦБ РА не вернул курсы на эту дату. Укажите курс вручную");
        return rates;
    }
}
