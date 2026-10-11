package am.pulsar.tax;

import am.pulsar.tax.job.ReminderJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** API целиком: настоящий сервер, временный PostgreSQL (TestDatabaseConfig), заглушки ЦБ РА и Telegram. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {PulsarApplication.class, TestDatabaseConfig.class})
class ApiTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        r.add("pulsar.cba-url", Stubs::cbaUrl);
        r.add("pulsar.telegram-token", () -> "TOKEN");
        r.add("pulsar.telegram-api", Stubs::telegramUrl);
        r.add("pulsar.allowed-origins", () -> "https://app.pulsar.am/");
    }


    @LocalServerPort
    int port;
    @Autowired
    ReminderJob reminders;

    ApiClient client() {
        return new ApiClient(port);
    }

    @Test
    void root() throws Exception {
        assertEquals("{\"service\":\"pulsar-tax-api\",\"status\":\"ok\"}".length(), client().call("GET", "/", null).body().length());
        assertEquals("ok", client().json("GET", "/", null).path("status").asString());
    }

    @Test
    void requiresLogin() throws Exception {
        ApiClient c = client();
        assertEquals(401, c.status("GET", "/api/profile", null));
        assertEquals("Войдите в Pulsar Tax", c.error("GET", "/api/auth/me", null));
        assertEquals(401, c.status("GET", "/api/unknown", null));
        assertEquals(200, c.status("GET", "/api/rules", null)); // справочник открыт
        c.register("login@example.am");
        assertEquals(200, c.status("GET", "/api/profile", null));
        assertEquals(404, c.status("GET", "/api/unknown", null));
        assertEquals(204, c.status("POST", "/api/auth/logout", null));
        assertEquals(401, c.status("GET", "/api/profile", null));
        assertEquals(401, c.status("POST", "/api/auth/login", "{\"email\":\"login@example.am\",\"password\":\"не тот\"}"));
        assertEquals(200, c.status("POST", "/api/auth/login", "{\"email\":\" LOGIN@example.am \",\"password\":\"пароль-для-теста\"}"));
        assertEquals(409, c.status("POST", "/api/auth/register", "{\"email\":\"login@example.am\",\"password\":\"пароль-для-теста\"}"));
        assertEquals(400, client().status("POST", "/api/auth/register", "{\"email\":\"нет\",\"password\":\"пароль-для-теста\"}"));
        assertEquals(400, client().status("POST", "/api/auth/register", "{\"email\":\"short@example.am\",\"password\":\"123\"}"));
    }

    @Test
    void parallelFirstRequestsCreateOneProfile() throws Exception {
        ApiClient c = client().register("parallel@example.am");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int n = i;
            calls.add(() -> c.status("GET", n % 2 == 0 ? "/api/profile" : "/api/summary", null));
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            for (Future<Integer> f : pool.invokeAll(calls)) assertEquals(200, f.get());
        }
    }

    @Test
    void lockoutAfterFiveFailures() throws Exception {
        ApiClient c = client().register("lock@example.am");
        for (int i = 0; i < 5; i++) {
            assertEquals(401, c.status("POST", "/api/auth/login", "{\"email\":\"lock@example.am\",\"password\":\"wrong-pass\"}"));
        }
        assertEquals(429, c.status("POST", "/api/auth/login", "{\"email\":\"lock@example.am\",\"password\":\"пароль-для-теста\"}"));
    }

    @Test
    void cookieFlags() throws Exception {
        HttpResponse<String> r = client().call("POST", "/api/auth/register",
                "{\"email\":\"cookie@example.am\",\"password\":\"пароль-для-теста\",\"lang\":\"hy\"}");
        String cookie = r.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("pulsar_session="));
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict") && cookie.contains("Max-Age=2592000"));
        assertFalse(cookie.contains("Secure"));
        JsonNode me = ApiClient.JSON.readTree(r.body());
        assertEquals("hy", me.path("lang").asString());
        assertTrue(me.path("telegramEnabled").asBoolean());
        assertTrue(me.path("calendarPath").asString().matches("/cal/[0-9a-f]{40}\\.ics"));
        assertTrue(me.path("token").isMissingNode());
    }

    @Test
    void bearerToken() throws Exception {
        ApiClient app = client();
        HttpResponse<String> r = app.call("POST", "/api/auth/register",
                "{\"email\":\"phone@example.am\",\"password\":\"пароль-для-теста\",\"tokenAuth\":true}");
        assertEquals(201, r.statusCode());
        assertTrue(r.headers().allValues("Set-Cookie").isEmpty(), "приложению cookie не выдаётся");
        app.bearer = ApiClient.JSON.readTree(r.body()).path("token").asString();
        assertEquals(64, app.bearer.length());
        assertEquals("phone@example.am", app.json("GET", "/api/auth/me", null).path("email").asString());

        // Смена пароля выходит со всех устройств, текущему выдаётся новый токен.
        ApiClient other = client();
        other.call("POST", "/api/auth/login", "{\"email\":\"phone@example.am\",\"password\":\"пароль-для-теста\"}");
        assertEquals(200, other.status("GET", "/api/profile", null));
        JsonNode changed = app.json("POST", "/api/account/password",
                "{\"oldPassword\":\"пароль-для-теста\",\"newPassword\":\"новый-пароль-1\"}");
        assertNotEquals(app.bearer, changed.path("token").asString());
        assertEquals(401, app.status("GET", "/api/profile", null));
        assertEquals(401, other.status("GET", "/api/profile", null));
        app.bearer = changed.path("token").asString();
        assertEquals(200, app.status("GET", "/api/profile", null));
        assertEquals(400, app.status("POST", "/api/account/password", "{\"oldPassword\":\"не тот\",\"newPassword\":\"новый-пароль-2\"}"));
        assertEquals("en", app.json("PUT", "/api/account", "{\"lang\":\"en\"}").path("lang").asString());
        assertEquals(400, app.status("PUT", "/api/account", "{\"lang\":\"de\"}"));
    }

    @Test
    void incomesInvoicesPayments() throws Exception {
        ApiClient c = client().register("flow@example.am");
        JsonNode profile = c.json("PUT", "/api/profile", "{\"name\":\"ИП Тест\",\"regime\":\"TURNOVER\",\"activity\":\"SERVICES\"}");
        assertEquals("TURNOVER", profile.path("regime").asString());

        JsonNode income = c.json("POST", "/api/incomes", "{\"date\":\"2026-02-10\",\"amount\":\"150000,50\",\"client\":\"А\"}");
        assertEquals("150000.5", income.path("amount").toString());
        JsonNode usd = c.json("POST", "/api/incomes",
                "{\"date\":\"2026-03-01\",\"currency\":\"USD\",\"originalAmount\":100.25,\"rate\":387.5}");
        assertEquals("38847", usd.path("amount").toString()); // округление до драма

        JsonNode inv = c.json("POST", "/api/invoices",
                "{\"client\":\"Б\",\"date\":\"2026-04-01\",\"items\":[{\"name\":\"Работа\",\"quantity\":2,\"price\":\"1000.4\"}]}");
        assertEquals("PT-2026-001", inv.path("number").asString());
        assertEquals("2001", inv.path("total").toString());
        assertEquals("PT-2026-002", c.json("POST", "/api/invoices",
                "{\"client\":\"Б\",\"date\":\"2026-04-02\",\"items\":[{\"name\":\"x\",\"quantity\":1,\"price\":1}]}").path("number").asString());
        assertEquals(409, c.status("POST", "/api/invoices",
                "{\"number\":\"pt-2026-001\",\"client\":\"Б\",\"items\":[{\"name\":\"x\",\"quantity\":1,\"price\":1}]}"));
        long id = inv.path("id").asLong();

        assertEquals("PAID", c.json("POST", "/api/invoices/" + id + "/paid?date=2026-04-05", null).path("status").asString());
        c.json("POST", "/api/invoices/" + id + "/paid?date=2026-04-06", null); // повторно доход не записывается
        JsonNode incomes = c.json("GET", "/api/incomes?year=2026", null);
        assertEquals(3, incomes.size());
        JsonNode paid = incomes.get(0); // самый поздний
        assertEquals(id, paid.path("invoiceId").asLong());
        assertEquals("Оплата по счёту PT-2026-001", paid.path("description").asString());

        // Удалили доход по счёту — счёт снова не оплачен; удалили счёт — его доход исчез.
        assertEquals(204, c.status("DELETE", "/api/incomes/" + paid.path("id").asLong(), null));
        assertEquals("SENT", c.json("GET", "/api/invoices/" + id, null).path("status").asString());
        c.json("POST", "/api/invoices/" + id + "/paid?date=2026-04-07", null);
        assertEquals(204, c.status("DELETE", "/api/invoices/" + id, null));
        assertEquals(2, c.json("GET", "/api/incomes?year=2026", null).size());
        assertEquals(404, c.status("GET", "/api/invoices/" + id, null));

        assertEquals("E-1", c.json("PUT", "/api/invoices/" + c.json("GET", "/api/invoices", null).get(0).path("id").asLong()
                + "/einvoice", "{\"eInvoiceNumber\":\"E-1\"}").path("eInvoiceNumber").asString());

        JsonNode payment = c.json("POST", "/api/payments", "{\"year\":2026,\"quarter\":1,\"date\":\"2026-04-15\",\"amount\":10000}");
        assertEquals(1, c.json("GET", "/api/payments?year=2026", null).size());
        JsonNode summary = c.json("GET", "/api/summary?year=2026", null);
        assertEquals("188847.5", summary.path("totalIncome").toString());
        assertEquals("18885", summary.path("totalTax").toString());
        assertEquals("10000", summary.path("totalPaid").toString());
        assertEquals("8885", summary.path("totalDue").toString());
        assertEquals("2026-04-20", summary.path("quarters").get(0).path("dueDate").asString());
        assertEquals(204, c.status("DELETE", "/api/payments/" + payment.path("id").asLong(), null));
        assertEquals(400, c.status("GET", "/api/summary?year=19", null));
    }

    @Test
    void importDedupe() throws Exception {
        ApiClient c = client().register("import@example.am");
        String batch = "{\"items\":[{\"date\":\"2026-05-01\",\"amount\":100,\"importKey\":\"k1\"},"
                + "{\"date\":\"2026-05-02\",\"amount\":200,\"importKey\":\"k2\"},"
                + "{\"date\":\"2026-05-02\",\"amount\":200,\"importKey\":\"k2\"}]}";
        JsonNode first = c.json("POST", "/api/incomes/import", batch);
        assertEquals(2, first.path("added").asInt());
        assertEquals(1, first.path("skipped").asInt());
        JsonNode again = c.json("POST", "/api/incomes/import", batch);
        assertEquals(0, again.path("added").asInt());
        assertEquals(3, again.path("skipped").asInt());
        assertEquals("Строка 2: Сумма дохода должна быть больше нуля", c.error("POST", "/api/incomes/import",
                "{\"items\":[{\"date\":\"2026-05-01\",\"amount\":1},{\"date\":\"2026-05-01\",\"amount\":0}]}"));
        assertEquals(2, c.json("GET", "/api/incomes?year=2026", null).size()); // ошибка — ничего не добавлено
    }

    @Test
    void usersSeeOnlyTheirData() throws Exception {
        ApiClient a = client().register("a@example.am");
        ApiClient b = client().register("b@example.am");
        long id = a.json("POST", "/api/incomes", "{\"date\":\"2026-01-10\",\"amount\":500}").path("id").asLong();
        long inv = a.json("POST", "/api/invoices", "{\"client\":\"К\",\"items\":[{\"name\":\"x\",\"quantity\":1,\"price\":1}]}")
                .path("id").asLong();
        assertEquals(0, b.json("GET", "/api/incomes?year=2026", null).size());
        assertEquals(404, b.status("DELETE", "/api/incomes/" + id, null));
        assertEquals(404, b.status("GET", "/api/invoices/" + inv, null));
        assertEquals(404, b.status("POST", "/api/invoices/" + inv + "/paid", null));
        assertEquals(1, a.json("GET", "/api/incomes?year=2026", null).size());
    }

    @Test
    void validation() throws Exception {
        ApiClient c = client().register("valid@example.am");
        assertEquals("Ожидается Content-Type: application/json",
                c.error("POST", "/api/incomes", "{}", "Content-Type", "text/plain"));
        assertEquals(415, c.status("POST", "/api/incomes", "date=1", "Content-Type", "application/x-www-form-urlencoded"));
        assertEquals(400, c.status("POST", "/api/incomes", "{не json"));
        assertEquals("Сумма дохода: не больше 2 знаков после запятой",
                c.error("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":\"10.555\"}"));
        assertEquals(400, c.status("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":\"1e999999999\"}"));
        assertEquals(400, c.status("POST", "/api/incomes", "{\"date\":\"01.01.2026\",\"amount\":1}"));
        assertEquals(400, c.status("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"currency\":\"XYZ\",\"amount\":1}"));
        assertEquals("Для своего вида деятельности укажите ставку налога",
                c.error("PUT", "/api/profile", "{\"regime\":\"TURNOVER\",\"activity\":\"CUSTOM\"}"));
        assertEquals(400, c.status("PUT", "/api/profile", "{\"regime\":\"TURNOVER\",\"activity\":\"CUSTOM\",\"customRate\":101}"));
        assertEquals("Добавьте хотя бы одну позицию в счёт", c.error("POST", "/api/invoices", "{\"client\":\"К\",\"items\":[]}"));
        assertEquals(400, c.status("POST", "/api/payments", "{\"year\":2026,\"quarter\":5,\"date\":\"2026-01-01\",\"amount\":1}"));
        assertEquals(404, c.status("DELETE", "/api/payments/999999", null));
        assertEquals(404, c.status("PATCH", "/api/incomes", "{}"));
    }

    @Test
    void requestSourceAndCors() throws Exception {
        ApiClient c = client().register("cors@example.am");
        // DNS rebinding: чужое имя хоста при работе на 127.0.0.1.
        assertEquals("Запрос с чужого адреса отклонён", c.error("GET", "/api/rules", null, "Host", "evil.example"));
        // CSRF: изменяющий запрос со страницы чужого сайта.
        assertEquals(403, c.status("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":1}",
                "Content-Type", "application/json", "Origin", "https://evil.example"));
        assertEquals(201, c.status("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":1}",
                "Content-Type", "application/json", "Origin", "https://app.pulsar.am"));

        HttpResponse<String> pre = c.call("OPTIONS", "/api/incomes", null, "Origin", "http://localhost:5173",
                "Access-Control-Request-Method", "POST");
        assertEquals(204, pre.statusCode());
        assertEquals("http://localhost:5173", pre.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("true", pre.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
        assertTrue(pre.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().contains("Authorization"));
        assertEquals(403, c.status("OPTIONS", "/api/incomes", null, "Origin", "https://evil.example"));
        HttpResponse<String> get = c.call("GET", "/api/rules", null, "Origin", "https://app.pulsar.am");
        assertEquals("https://app.pulsar.am", get.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(c.call("GET", "/api/rules", null, "Origin", "https://evil.example").headers()
                .firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void expensesProfilesRatesExport() throws Exception {
        ApiClient c = client().register("free@example.am");
        JsonNode e = c.json("POST", "/api/expenses", "{\"date\":\"2026-03-01\",\"amount\":2500,\"category\":\"RENT\"}");
        assertEquals("RENT", e.path("category").asString());
        assertEquals("OTHER", c.json("POST", "/api/expenses", "{\"date\":\"2026-03-02\",\"amount\":1}").path("category").asString());
        assertEquals(400, c.status("POST", "/api/expenses", "{\"date\":\"2026-03-02\",\"amount\":-1}"));
        assertEquals("2501", c.json("GET", "/api/summary?year=2026", null).path("totalExpenses").toString());
        assertEquals(204, c.status("DELETE", "/api/expenses/" + e.path("id").asLong(), null));

        String mapping = "{\"date\":0,\"amount\":1,\"debit\":-1,\"desc\":2,\"currencyCol\":-1,\"dateFormat\":\"dd.mm.yyyy\",\"currency\":\"USD\"}";
        assertEquals(204, c.status("PUT", "/api/import-profiles/abcd-1234", mapping));
        assertEquals(400, c.status("PUT", "/api/import-profiles/abcd-1234", mapping.replace("dd.mm.yyyy", "dd/yy")));
        assertEquals(404, c.status("PUT", "/api/import-profiles/BAD!", mapping));
        JsonNode profiles = c.json("GET", "/api/import-profiles", null);
        assertEquals("USD", profiles.path("abcd-1234").path("currency").asString());
        assertEquals(2, profiles.path("abcd-1234").path("desc").asInt());
        for (int i = 0; i < 22; i++) c.call("PUT", "/api/import-profiles/sig-" + (1000 + i), mapping);
        assertEquals(20, c.json("GET", "/api/import-profiles", null).size()); // самые старые ушли

        int calls = Stubs.cbaCalls.get();
        JsonNode rates = c.json("GET", "/api/rates?date=2026-03-02", null);
        assertEquals("387.5", rates.path("rates").path("USD").toString());
        assertEquals("2.551", rates.path("rates").path("JPY").toString());
        c.json("GET", "/api/rates?date=2026-03-02", null);
        assertEquals(calls + 1, Stubs.cbaCalls.get(), "прошедшая дата берётся из кэша");
        assertEquals(400, c.status("GET", "/api/rates?date=2999-01-01", null));
        Stubs.cbaDown = true;
        try {
            assertEquals("ЦБ РА не ответил (код 500)", c.error("GET", "/api/rates?date=2026-03-03", null));
        } finally {
            Stubs.cbaDown = false;
        }

        c.json("POST", "/api/incomes", "{\"date\":\"2026-01-10\",\"amount\":1000,\"client\":\"=1+1\"}");
        HttpResponse<String> csv = c.call("GET", "/api/export/incomes.csv", null);
        assertTrue(csv.headers().firstValue("Content-Type").orElseThrow().startsWith("text/csv"));
        assertEquals("attachment; filename=\"incomes.csv\"", csv.headers().firstValue("Content-Disposition").orElseThrow());
        assertTrue(csv.body().startsWith("﻿date;amount_amd"));
        assertTrue(csv.body().contains("'=1+1"));
        assertTrue(c.call("GET", "/api/export/expenses.csv", null).body().contains("OTHER"));
        assertTrue(c.call("GET", "/api/export/invoices.csv", null).body().startsWith("﻿number;"));
        JsonNode data = c.json("GET", "/api/export/data.json", null);
        assertEquals(1, data.path("incomes").size());
        assertEquals(20, data.path("importProfiles").size());
    }

    @Test
    void calendarByToken() throws Exception {
        ApiClient c = client().register("cal@example.am");
        c.json("PUT", "/api/profile", "{\"regime\":\"TURNOVER\",\"activity\":\"SERVICES\"}");
        String path = c.json("GET", "/api/auth/me", null).path("calendarPath").asString();
        HttpResponse<String> ics = client().call("GET", path, null); // без cookie
        assertEquals(200, ics.statusCode());
        assertTrue(ics.headers().firstValue("Content-Type").orElseThrow().startsWith("text/calendar"));
        assertTrue(ics.body().startsWith("BEGIN:VCALENDAR"));
        assertEquals(404, client().status("GET", "/cal/" + "0".repeat(40) + ".ics", null));
    }

    @Test
    void telegramLinkAndReminders() throws Exception {
        ApiClient c = client().register("tg@example.am");
        c.json("PUT", "/api/account", "{\"lang\":\"hy\"}");
        c.json("PUT", "/api/profile", "{\"regime\":\"TURNOVER\",\"activity\":\"SERVICES\"}");
        c.json("POST", "/api/incomes", "{\"date\":\"2026-08-01\",\"amount\":1000000}");
        JsonNode link = null;
        for (int i = 0; i < 50 && link == null; i++) {
            HttpResponse<String> r = c.call("POST", "/api/account/telegram", null);
            if (r.statusCode() == 200) link = ApiClient.JSON.readTree(r.body());
            else Thread.sleep(100); // бот ещё не ответил на getMe
        }
        assertNotNull(link);
        String code = link.path("code").asString();
        assertEquals("https://t.me/pulsar_test_bot?start=" + code, link.path("link").asString());

        Stubs.incoming.add("/start " + code);
        for (int i = 0; i < 100 && !c.json("GET", "/api/auth/me", null).path("telegramLinked").asBoolean(); i++) Thread.sleep(100);
        assertTrue(c.json("GET", "/api/auth/me", null).path("telegramLinked").asBoolean());
        assertTrue(Stubs.sent.stream().anyMatch(m -> m.contains("chat_id=555") && m.contains("միացված")));

        int before = Stubs.sent.size();
        reminders.sendReminders(LocalDate.of(2026, 10, 17)); // за 3 дня до 20 октября
        assertEquals(before + 1, Stubs.sent.size());
        String text = Stubs.sent.getLast();
        assertTrue(text.contains("3 օրից") && text.contains("100000 ֏"), text);
        reminders.sendReminders(LocalDate.of(2026, 10, 17)); // второй раз за день не шлём
        assertEquals(before + 1, Stubs.sent.size());

        assertFalse(c.json("DELETE", "/api/account/telegram", null).path("telegramLinked").asBoolean());
    }
}
