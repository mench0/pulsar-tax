package am.pulsar.tax.client;

import am.pulsar.tax.config.PulsarProperties;
import am.pulsar.tax.entity.FxRate;
import am.pulsar.tax.exception.ApiException;
import am.pulsar.tax.repository.FxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Официальные курсы ЦБ РА (api.cba.am, SOAP-метод ExchangeRatesByDate) — драмов за единицу валюты.
 * Кэш в таблице fx_rates: курсы за прошедшие даты не меняются и хранятся всегда, за сегодня — час.
 */
@Service
public class CbaRates {

    private static final Pattern RATE = Pattern.compile(
            "<ExchangeRate>.*?<ISO>(\\w{3})</ISO>.*?<Amount>([\\d.]+)</Amount>.*?<Rate>([\\d.]+)</Rate>.*?</ExchangeRate>",
            Pattern.DOTALL);
    private static final Duration TODAY_TTL = Duration.ofHours(1);

    private final String url;
    private final FxRateRepository repo;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CbaRates(PulsarProperties props, FxRateRepository repo, TransactionTemplate tx) {
        this.url = props.cbaUrl();
        this.repo = repo;
        this.tx = tx;
    }

    /** Курсы на дату: ISO-код → драмов за 1 единицу. Валюты, которых нет в Pulsar, тоже отдаются. */
    public Map<String, BigDecimal> on(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) throw ApiException.badRequest("Курс на будущую дату ещё не известен");
        List<FxRate> cached = repo.findByDateOrderByCurrency(date);
        Instant now = Instant.now();
        boolean fresh = !cached.isEmpty() && (date.isBefore(today)
                || cached.getFirst().getLoadedAt().plus(TODAY_TTL).isAfter(now));
        if (fresh) {
            Map<String, BigDecimal> m = new LinkedHashMap<>();
            for (FxRate r : cached) m.put(r.getCurrency(), r.getRate().stripTrailingZeros());
            return m;
        }
        // Запрос к ЦБ — вне транзакции, чтобы не держать соединение с базой, пока ждём ответа.
        Map<String, BigDecimal> rates = parse(fetch(date));
        tx.executeWithoutResult(s -> {
            repo.deleteByDate(date);
            repo.flush();
            rates.forEach((code, rate) -> repo.save(new FxRate(date, code, rate, now)));
        });
        return rates;
    }

    private String fetch(LocalDate date) {
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
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new ApiException(503, "ЦБ РА не ответил (код " + res.statusCode() + ")");
            return res.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(503, "Не удалось получить курс ЦБ РА. Укажите курс вручную");
        }
    }

    /** Разбор ответа ЦБ: курс делится на Amount (у некоторых валют курс дан за 10 или 100 единиц). */
    public static Map<String, BigDecimal> parse(String xml) {
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
