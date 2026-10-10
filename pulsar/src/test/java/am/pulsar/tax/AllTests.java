package am.pulsar.tax;

import am.pulsar.tax.Model.*;
import am.pulsar.tax.TaxRules.ThresholdStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Тесты без внешних библиотек. Запуск: java -cp build/classes:build/test am.pulsar.tax.AllTests */
public final class AllTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        run("JSON: запись и разбор", AllTests::jsonRoundTrip);
        run("JSON: экранирование и ошибки", AllTests::jsonEscapesAndErrors);
        run("Налог: микробизнес платит 0", AllTests::microZero);
        run("Налог: ставка по виду деятельности", AllTests::turnoverRates);
        run("Налог: своя ставка и округление", AllTests::customRate);
        run("Налог: общая система не считается", AllTests::generalRegime);
        run("Порог: статусы", AllTests::thresholds);
        run("Срок уплаты за квартал", AllTests::quarterDue);
        run("Хранилище: номера счетов и сохранение", AllTests::storePersistence);
        run("API: счёт → оплата → доход → налог", AllTests::apiFlow);
        run("API: ошибки проверки", AllTests::apiValidation);
        System.out.println();
        System.out.println("Пройдено: " + passed + ", упало: " + failed);
        if (failed > 0) System.exit(1);
    }

    interface Check {
        void run() throws Exception;
    }

    private static void run(String name, Check c) {
        try {
            c.run();
            passed++;
            System.out.println("  ✓ " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  ✗ " + name + ": " + t);
        }
    }

    private static void eq(Object expected, Object actual) {
        boolean same = expected instanceof BigDecimal e && actual instanceof BigDecimal a
                ? e.compareTo(a) == 0
                : java.util.Objects.equals(expected, actual);
        if (!same) throw new AssertionError("ожидалось <" + expected + ">, получено <" + actual + ">");
    }

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static Profile profile(TaxRegime r, ActivityType a) {
        Profile p = new Profile();
        p.regime = r;
        p.activity = a;
        return p;
    }

    // ---------- JSON ----------

    static void jsonRoundTrip() {
        String text = "{\"a\":1,\"b\":[true,false,null],\"c\":\"Ереван\",\"d\":-2.50,\"e\":{}}";
        Map<String, Object> m = Json.parseObject(text);
        eq(d("1"), m.get("a"));
        eq(List.of(true, false), ((List<?>) m.get("b")).subList(0, 2));
        eq("Ереван", m.get("c"));
        eq(d("-2.5"), m.get("d"));
        eq("{\"a\":1,\"b\":[true,false,null],\"c\":\"Ереван\",\"d\":-2.5,\"e\":{}}", Json.write(m));
    }

    static void jsonEscapesAndErrors() {
        String s = "кавычка \" слэш \\ строка\nтаб\t";
        eq(s, Json.parse(Json.write(s)));
        eq("A", Json.parse("\"\\u0041\""));
        for (String bad : new String[]{"{", "{\"a\" 1}", "[1,]", "\"abc", "tru", "{} x"}) {
            try {
                Json.parse(bad);
                throw new AssertionError("должна быть ошибка для " + bad);
            } catch (IllegalArgumentException expected) {
                // ок
            }
        }
    }

    // ---------- Налоги ----------

    static void microZero() {
        BigDecimal rate = TaxRules.ratePercent(profile(TaxRegime.MICRO, ActivityType.SERVICES));
        eq(d("0"), TaxRules.tax(d("5000000"), rate));
    }

    static void turnoverRates() {
        eq(d("100000"), TaxRules.tax(d("1000000"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.SERVICES))));
        eq(d("10000"), TaxRules.tax(d("1000000"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.HIGH_TECH))));
    }

    static void customRate() {
        Profile p = profile(TaxRegime.TURNOVER, ActivityType.CUSTOM);
        p.customRate = d("5");
        eq(d("16667"), TaxRules.tax(d("333333"), TaxRules.ratePercent(p)));
        p.customRate = null;
        eq(null, TaxRules.tax(d("100"), TaxRules.ratePercent(p)));
    }

    static void generalRegime() {
        eq(null, TaxRules.ratePercent(profile(TaxRegime.GENERAL, ActivityType.TRADE)));
        eq(null, TaxRules.threshold(TaxRegime.GENERAL));
        eq(ThresholdStatus.NONE, TaxRules.status(d("1"), null));
    }

    static void thresholds() {
        BigDecimal limit = TaxRules.MICRO_THRESHOLD;
        eq(ThresholdStatus.OK, TaxRules.status(d("19199999"), limit));
        eq(ThresholdStatus.WARNING, TaxRules.status(d("19200000"), limit));
        eq(ThresholdStatus.WARNING, TaxRules.status(d("24000000"), limit));
        eq(ThresholdStatus.EXCEEDED, TaxRules.status(d("24000001"), limit));
        eq(d("50.0"), TaxRules.percentOf(d("12000000"), limit));
    }

    static void quarterDue() {
        eq(LocalDate.of(2026, 4, 20), Summary.quarterDue(2026, 1));
        eq(LocalDate.of(2026, 10, 20), Summary.quarterDue(2026, 3));
        eq(LocalDate.of(2027, 1, 20), Summary.quarterDue(2026, 4));
    }

    // ---------- Хранилище ----------

    static void storePersistence() throws Exception {
        Path dir = Files.createTempDirectory("pulsar-test");
        Path file = dir.resolve("data.json");
        Store s = new Store(file);
        Invoice a = invoice("Клиент А", "2026-03-01");
        s.addInvoice(a);
        Invoice b = invoice("Клиент Б", "2026-03-02");
        s.addInvoice(b);
        eq("PT-2026-001", a.number);
        eq("PT-2026-002", b.number);
        s.markPaid(a.id, LocalDate.of(2026, 3, 5));
        s.markPaid(a.id, LocalDate.of(2026, 3, 6)); // повтор не создаёт второй доход
        s.deleteInvoice(b.id);
        Invoice c = invoice("Клиент В", "2026-03-03");
        s.addInvoice(c);
        eq("PT-2026-002", c.number); // номер удалённого счёта переиспользуется, дублей нет

        Store reloaded = new Store(file);
        eq(1, reloaded.incomes(2026).size());
        eq(d("1000"), reloaded.incomes(2026).get(0).amount);
        eq(InvoiceStatus.PAID, reloaded.invoice(a.id).status);
        eq(2, reloaded.invoices().size());
        Invoice next = invoice("Клиент Г", "2026-03-04");
        reloaded.addInvoice(next);
        eq(true, next.id > c.id);
    }

    private static Invoice invoice(String client, String date) {
        Invoice inv = new Invoice();
        inv.client = client;
        inv.date = LocalDate.parse(date);
        inv.items = List.of(new Item("Услуга", d("2"), d("500")));
        return inv;
    }

    // ---------- HTTP ----------

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static HttpResponse<String> call(int port, String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("Content-Type", "application/json");
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static void apiFlow() throws Exception {
        Server server = new Server(new Store(null), "127.0.0.1", 0);
        server.start();
        try {
            int port = server.port();
            int year = LocalDate.now().getYear();
            eq(200, call(port, "PUT", "/api/profile",
                    "{\"name\":\"Тест\",\"regime\":\"TURNOVER\",\"activity\":\"SERVICES\"}").statusCode());

            HttpResponse<String> created = call(port, "POST", "/api/invoices",
                    "{\"client\":\"ООО Клиент\",\"date\":\"" + year + "-02-10\","
                            + "\"items\":[{\"name\":\"Разработка\",\"quantity\":2,\"price\":500000}]}");
            eq(201, created.statusCode());
            Map<String, Object> inv = Json.parseObject(created.body());
            eq("PT-" + year + "-001", inv.get("number"));
            eq(d("1000000"), inv.get("total"));

            HttpResponse<String> paid = call(port, "POST",
                    "/api/invoices/" + inv.get("id") + "/paid?date=" + year + "-02-15", null);
            eq(200, paid.statusCode());
            eq("PAID", Json.parseObject(paid.body()).get("status"));

            Map<String, Object> summary = Json.parseObject(call(port, "GET", "/api/summary?year=" + year, null).body());
            eq(d("1000000"), summary.get("totalIncome"));
            eq(d("100000"), summary.get("totalTax"));
            eq(d("10"), summary.get("ratePercent"));

            HttpResponse<String> page = call(port, "GET", "/", null);
            eq(200, page.statusCode());
            eq(true, page.body().contains("Pulsar"));
            eq(404, call(port, "GET", "/../secret.txt", null).statusCode());
        } finally {
            server.stop();
        }
    }

    static void apiValidation() throws Exception {
        Server server = new Server(new Store(null), "127.0.0.1", 0);
        server.start();
        try {
            int port = server.port();
            HttpResponse<String> r = call(port, "POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":-5}");
            eq(400, r.statusCode());
            eq(true, Json.parseObject(r.body()).containsKey("error"));
            eq(400, call(port, "POST", "/api/incomes", "не json").statusCode());
            eq(400, call(port, "POST", "/api/invoices", "{\"client\":\"X\",\"items\":[]}").statusCode());
            eq(400, call(port, "PUT", "/api/profile",
                    "{\"regime\":\"TURNOVER\",\"activity\":\"CUSTOM\"}").statusCode());
            eq(404, call(port, "DELETE", "/api/incomes/999", null).statusCode());
            eq(404, call(port, "GET", "/api/unknown", null).statusCode());
            eq(400, call(port, "GET", "/api/summary?year=abc", null).statusCode());
        } finally {
            server.stop();
        }
    }
}
