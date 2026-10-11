package am.pulsar.tax;

import am.pulsar.tax.Model.*;
import am.pulsar.tax.Server.Options;
import am.pulsar.tax.TaxRules.ThresholdStatus;

import java.math.BigDecimal;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
        run("Правила: по годам", AllTests::rulesByYear);
        run("Правила: вид деятельности и режим", AllTests::activityRegime);
        run("Числа: лимиты и округление до драма", AllTests::numberLimits);
        run("Хранилище: счёт и доход согласованы", AllTests::invoiceIncomeConsistency);
        run("Хранилище: номера счетов и сохранение", AllTests::storePersistence);
        run("Хранилище: импорт без дублей", AllTests::importDedupe);
        run("Хранилище: старый формат файла", AllTests::legacyFileLoads);
        run("Доход в валюте", AllTests::foreignCurrencyIncome);
        run("Сводка: уплата и остаток к уплате", AllTests::summaryPayments);
        run("Учётные записи: вход, блокировка, пароль", AllTests::accountsAuth);
        run("Учётные записи: перенос старых данных", AllTests::accountsLegacyMigration);
        run("Календарь .ics", AllTests::calendarIcs);
        run("Напоминание на сегодня", AllTests::reminderText);
        run("CSV: экранирование", AllTests::csvExport);
        run("Резервные копии", AllTests::backups);
        run("Telegram: привязка и напоминание", AllTests::telegramFlow);
        run("API: без входа — 401", AllTests::apiRequiresLogin);
        run("API: счёт → оплата → доход → налог → уплата", AllTests::apiFlow);
        run("API: данные пользователей разделены", AllTests::apiIsolation);
        run("API: ошибки проверки", AllTests::apiValidation);
        run("API: защита от чужих сайтов", AllTests::apiRequestSource);
        run("API: правила, импорт, экспорт, календарь", AllTests::apiMisc);
        run("API: CORS для отдельного фронтенда", AllTests::apiCors);
        run("Настройки cookie", AllTests::cookieOptions);
        run("API: вход по токену для мобильного приложения", AllTests::apiBearerToken);
        run("Курсы ЦБ: разбор ответа", AllTests::ratesParse);
        run("API: расходы, настройки импорта, курсы ЦБ", AllTests::apiExpensesProfilesRates);
        run("Хранилище: расходы и настройки импорта сохраняются", AllTests::storeExpensesProfiles);
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
        BigDecimal rate = TaxRules.ratePercent(profile(TaxRegime.MICRO, ActivityType.SERVICES), 2026);
        eq(d("0"), TaxRules.tax(d("5000000"), rate));
    }

    static void turnoverRates() {
        eq(d("100000"), TaxRules.tax(d("1000000"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.SERVICES), 2026)));
        eq(d("10000"), TaxRules.tax(d("1000000"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.HIGH_TECH), 2026)));
    }

    static void customRate() {
        Profile p = profile(TaxRegime.TURNOVER, ActivityType.CUSTOM);
        p.customRate = d("5");
        eq(d("16667"), TaxRules.tax(d("333333"), TaxRules.ratePercent(p, 2026)));
        p.customRate = null;
        eq(null, TaxRules.tax(d("100"), TaxRules.ratePercent(p, 2026)));
    }

    static void generalRegime() {
        eq(null, TaxRules.ratePercent(profile(TaxRegime.GENERAL, ActivityType.TRADE), 2026));
        eq(null, TaxRules.forYear(2026).threshold(TaxRegime.GENERAL));
        eq(ThresholdStatus.NONE, TaxRules.status(d("1"), null));
    }

    static void thresholds() {
        BigDecimal limit = TaxRules.forYear(2026).microThreshold();
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

    static void rulesByYear() {
        eq(2026, TaxRules.forYear(2026).fromYear());
        eq(2026, TaxRules.forYear(2030).fromYear()); // последняя редакция действует дальше
        eq(2026, TaxRules.forYear(2020).fromYear()); // до первой — берём первую
    }

    static void activityRegime() {
        TaxRules.YearRules r = TaxRules.forYear(2026);
        eq(List.of(TaxRegime.MICRO, TaxRegime.TURNOVER, TaxRegime.GENERAL), r.allowedRegimes(ActivityType.SERVICES));
        eq(List.of(TaxRegime.TURNOVER, TaxRegime.GENERAL), r.allowedRegimes(ActivityType.DESIGN));
        eq(List.of(TaxRegime.GENERAL), r.allowedRegimes(ActivityType.LEGAL));
        eq(List.of(TaxRegime.MICRO, TaxRegime.GENERAL), r.allowedRegimes(ActivityType.AGENCY));
        eq(true, TaxRules.regimeAllowed(profile(TaxRegime.TURNOVER, ActivityType.DESIGN), 2026));
        eq(false, TaxRules.regimeAllowed(profile(TaxRegime.MICRO, ActivityType.DESIGN), 2026));
        eq(null, TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.LEGAL), 2026));
        eq(d("10"), TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.DESIGN), 2026));
    }

    static void numberLimits() {
        for (String bad : new String[]{"1e999999999", "1000000000000", "1.00001"}) {
            try {
                new Fields(Map.of("x", bad)).dec("x", true);
                throw new AssertionError("должна быть ошибка для " + bad);
            } catch (ApiException expected) {
                eq(400, expected.status);
            }
        }
        eq(d("999999999999"), new Fields(Map.of("x", "999999999999")).dec("x", true));
        eq("1000", Json.write(new Fields(Map.of("x", d("1E+3"))).dec("x", true)));
        try {
            Codec.income(Map.of("date", "2026-01-01", "amount", "10.555"));
            throw new AssertionError("сумма дохода с 3 знаками должна отклоняться");
        } catch (ApiException expected) {
            // ок
        }
        eq(d("333"), new Item("x", d("0.333"), d("1000.5")).sum());
    }

    // ---------- Хранилище ----------

    static void invoiceIncomeConsistency() {
        Store s = new Store(null);
        Invoice a = s.addInvoice(invoice("А", "2026-03-01"));
        s.markPaid(a.id, LocalDate.of(2026, 3, 5));
        long incomeId = s.incomes(2026).get(0).id;
        s.deleteIncome(incomeId);
        eq(InvoiceStatus.SENT, s.invoice(a.id).status);
        s.markPaid(a.id, LocalDate.of(2026, 3, 6)); // оплату снова можно отметить
        eq(1, s.incomes(2026).size());

        Income manual = new Income();
        manual.date = LocalDate.of(2026, 3, 7);
        manual.amount = d("50");
        s.addIncome(manual);
        s.deleteInvoice(a.id);
        eq(1, s.incomes(2026).size()); // доход по счёту удалён, ручной остался
        eq(manual.id, s.incomes(2026).get(0).id);
    }

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

    static void importDedupe() {
        Store s = new Store(null);
        int[] r = s.importIncomes(List.of(income("2026-02-01", "100", "k1"), income("2026-02-02", "200", "k2"),
                income("2026-02-02", "200", "k2")));
        eq(2, r[0]);
        eq(1, r[1]);
        r = s.importIncomes(List.of(income("2026-02-01", "100", "k1"), income("2026-02-03", "300", null)));
        eq(1, r[0]); // ручная запись без ключа добавляется всегда
        eq(1, r[1]);
        eq(3, s.incomes(2026).size());
    }

    private static Income income(String date, String amount, String key) {
        Income i = new Income();
        i.date = LocalDate.parse(date);
        i.amount = d(amount);
        i.importKey = key;
        return i;
    }

    /** Файл версии 1: без уплат, валют и номеров e-invoice, суммы с 4 знаками. */
    static void legacyFileLoads() throws Exception {
        Path file = Files.createTempDirectory("pulsar-legacy").resolve("data.json");
        Files.writeString(file, """
                {"version":1,"nextId":3,
                 "profile":{"name":"ИП","tin":"","address":"","bankAccount":"","regime":"TURNOVER","activity":"SERVICES","customRate":null},
                 "incomes":[{"id":2,"date":"2026-01-10","amount":333.1665,"client":"А","description":"","invoiceId":1}],
                 "invoices":[{"id":1,"number":"PT-2026-001","date":"2026-01-05","dueDate":null,"client":"А","clientTin":"",
                   "status":"PAID","items":[{"name":"x","quantity":0.333,"price":1000.5,"sum":333.1665}],"total":333.1665}]}""");
        Store s = new Store(file);
        eq(d("333.17"), s.incomes(2026).get(0).amount);
        eq(Currency.AMD, s.incomes(2026).get(0).currency);
        eq("", s.invoice(1).eInvoiceNumber);
        eq(0, s.payments(2026).size());
        Income added = s.addIncome(income("2026-01-11", "1", null));
        eq(3L, added.id);
    }

    static void foreignCurrencyIncome() {
        Income i = Codec.income(Map.of("date", "2026-03-01", "currency", "USD", "originalAmount", "1000.50", "rate", "387.45"));
        eq(d("387644"), i.amount); // 1000.50 × 387.45 = 387643.7 → до драма
        eq(Currency.USD, i.currency);
        try {
            Codec.income(Map.of("date", "2026-03-01", "currency", "USD", "originalAmount", "10"));
            throw new AssertionError("без курса должна быть ошибка");
        } catch (ApiException expected) {
            eq(400, expected.status);
        }
    }

    static void summaryPayments() {
        Profile p = profile(TaxRegime.TURNOVER, ActivityType.SERVICES);
        TaxPayment pay = new TaxPayment();
        pay.year = 2026;
        pay.quarter = 1;
        pay.date = LocalDate.of(2026, 4, 15);
        pay.amount = d("30000");
        Map<String, Object> s = Summary.build(p, List.of(income("2026-02-01", "500000", null)), List.of(pay),
                2026, LocalDate.of(2026, 4, 1));
        Map<?, ?> q1 = (Map<?, ?>) ((List<?>) s.get("quarters")).get(0);
        eq(d("50000"), q1.get("tax"));
        eq(d("30000"), q1.get("paid"));
        eq(d("20000"), q1.get("due"));
        eq(d("20000"), s.get("totalDue"));
        eq(d("30000"), s.get("totalPaid"));
        Map<?, ?> first = (Map<?, ?>) ((List<?>) s.get("deadlines")).get(0);
        eq("TURNOVER_QUARTER", first.get("code"));
        eq(LocalDate.of(2026, 4, 20), first.get("date"));

        Map<String, Object> micro = Summary.build(profile(TaxRegime.MICRO, ActivityType.DESIGN), List.of(), List.of(),
                2026, LocalDate.of(2026, 4, 1));
        eq("REGIME_NOT_ALLOWED", ((Map<?, ?>) ((List<?>) micro.get("warnings")).get(0)).get("code"));
    }

    static void accountsAuth() {
        Accounts a = new Accounts(null, null);
        Accounts.User u = a.register(" Ann@Example.am ", "секретный-пароль");
        eq("ann@example.am", u.email());
        expectStatus(409, () -> a.register("ann@example.am", "другой-пароль"));
        expectStatus(400, () -> a.register("not-an-email", "длинный-пароль"));
        expectStatus(400, () -> a.register("b@example.am", "short"));

        String token = a.login("ANN@example.am", "секретный-пароль");
        eq(u, a.bySession(token));
        eq(null, a.bySession("чужой"));
        a.logout(token);
        eq(null, a.bySession(token));

        for (int i = 0; i < 5; i++) expectStatus(401, () -> a.login("ann@example.am", "неверный"));
        expectStatus(429, () -> a.login("ann@example.am", "секретный-пароль")); // заблокирован после 5 ошибок

        Accounts b = new Accounts(null, null);
        Accounts.User v = b.register("v@example.am", "старый-пароль");
        String t1 = b.login("v@example.am", "старый-пароль");
        expectStatus(400, () -> b.changePassword(v, "не тот", "новый-пароль"));
        b.changePassword(v, "старый-пароль", "новый-пароль");
        eq(null, b.bySession(t1)); // смена пароля выходит со всех устройств
        b.login("v@example.am", "новый-пароль");
    }

    static void accountsLegacyMigration() throws Exception {
        Path dir = Files.createTempDirectory("pulsar-accounts");
        Path legacy = dir.resolve("pulsar.json");
        Store old = new Store(legacy);
        old.addIncome(income("2026-01-10", "1000", null));

        Accounts a = new Accounts(dir, legacy);
        Accounts.User first = a.register("first@example.am", "пароль-первого");
        eq(1, a.store(first).incomes(2026).size());
        eq(false, Files.exists(legacy));
        Accounts.User second = a.register("second@example.am", "пароль-второго");
        eq(0, a.store(second).incomes(2026).size());

        Accounts reloaded = new Accounts(dir, legacy);
        String token = reloaded.login("first@example.am", "пароль-первого");
        eq(1, reloaded.store(reloaded.bySession(token)).incomes(2026).size());
    }

    static void calendarIcs() {
        String ics = Deadlines.ics(profile(TaxRegime.TURNOVER, ActivityType.SERVICES), "hy", LocalDate.of(2026, 10, 10));
        eq(true, ics.startsWith("BEGIN:VCALENDAR\r\n"));
        eq(true, ics.contains("DTSTART;VALUE=DATE:20261020"));
        eq(true, ics.contains("DTSTART;VALUE=DATE:20270120")); // 4 квартал платится в январе
        eq(false, ics.contains("DTSTART;VALUE=DATE:20260720")); // прошедшие сроки не попадают
        eq(true, ics.contains("TRIGGER:-P3D"));
        for (String line : ics.split("\r\n")) {
            if (line.getBytes(StandardCharsets.UTF_8).length > 75) throw new AssertionError("строка длиннее 75 байт: " + line);
        }
    }

    static void reminderText() {
        Store s = new Store(null);
        s.updateProfile(profile(TaxRegime.TURNOVER, ActivityType.SERVICES));
        s.addIncome(income("2026-08-01", "1000000", null));
        String text = Deadlines.reminder(s, "ru", LocalDate.of(2026, 10, 13));
        eq(true, text.contains("через 7 дн."));
        eq(true, text.contains("3 квартал 2026"));
        eq(true, text.contains("К уплате: 100000 ֏"));
        eq(null, Deadlines.reminder(s, "ru", LocalDate.of(2026, 10, 12)));
        eq(true, Deadlines.reminder(s, "en", LocalDate.of(2026, 10, 20)).contains("today"));
    }

    static void csvExport() {
        Income i = income("2026-01-10", "1000", null);
        i.client = "=HYPERLINK(\"x\")";
        i.description = "a;b";
        String csv = Csv.incomes(List.of(i));
        eq(true, csv.startsWith("﻿date;"));
        eq(true, csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        eq(true, csv.contains("\"a;b\""));
    }

    static void backups() throws Exception {
        Path dir = Files.createTempDirectory("pulsar-backup");
        Accounts a = new Accounts(dir, null);
        a.store(a.register("b@example.am", "пароль-бэкапа")).addIncome(income("2026-01-10", "1", null));
        for (int day = 1; day <= Backups.KEEP + 3; day++) Backups.run(dir, LocalDate.of(2026, 1, day));
        Path last = dir.resolve("backups").resolve("2026-01-17");
        eq(true, Files.exists(last.resolve("users.json")));
        eq(true, Files.exists(last.resolve("users").resolve("1.json")));
        try (var s = Files.list(dir.resolve("backups"))) {
            eq((long) Backups.KEEP, s.count());
        }
        eq(false, Files.exists(dir.resolve("backups").resolve("2026-01-01")));
    }

    /** Заглушка Bot API: отдаёт одно сообщение /start КОД и записывает отправленные сообщения. */
    static void telegramFlow() throws Exception {
        Accounts a = new Accounts(null, null);
        Accounts.User u = a.register("tg@example.am", "пароль-телеграма");
        a.setLang(u, "hy");
        Store s = a.store(u);
        s.updateProfile(profile(TaxRegime.TURNOVER, ActivityType.SERVICES));
        String code = a.telegramCode(u);

        List<String> sent = new java.util.concurrent.CopyOnWriteArrayList<>();
        com.sun.net.httpserver.HttpServer stub = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String reply = path.endsWith("/getUpdates") && body.contains("offset=0")
                    ? "{\"ok\":true,\"result\":[{\"update_id\":7,\"message\":{\"chat\":{\"id\":555},\"text\":\"/start " + code + "\"}}]}"
                    : path.endsWith("/getUpdates") ? "{\"ok\":true,\"result\":[]}" : "{\"ok\":true,\"result\":{}}";
            if (path.endsWith("/sendMessage")) sent.add(java.net.URLDecoder.decode(body, StandardCharsets.UTF_8));
            byte[] b = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        stub.start();
        try {
            Telegram tg = new Telegram("http://127.0.0.1:" + stub.getAddress().getPort(), "TOKEN", a);
            tg.pollOnce(0);
            eq(555L, u.telegramChatId());
            eq(true, sent.get(0).contains("chat_id=555") && sent.get(0).contains("միացված"));
            tg.pollOnce(0); // смещение сдвинулось, повторно сообщение не обрабатывается
            eq(1, sent.size());

            tg.sendReminders(LocalDate.of(2026, 10, 17)); // за 3 дня до 20 октября
            eq(2, sent.size());
            eq(true, sent.get(1).contains("3 օրից"));
            tg.sendReminders(LocalDate.of(2026, 10, 17)); // второй раз за день не шлём
            eq(2, sent.size());
        } finally {
            stub.stop(0);
        }
    }

    private static void expectStatus(int status, Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            eq(status, e.status);
            return;
        }
        throw new AssertionError("ожидалась ошибка " + status);
    }

    // ---------- HTTP ----------

    /** Сервер с учётными записями в памяти и клиент, который хранит cookie сессии. */
    private static final class Env implements AutoCloseable {
        private static final HttpClient CLIENT = HttpClient.newHttpClient();
        final Server server;
        final int port;
        private final boolean owner;
        private String cookie;

        private Env(Server server, boolean owner) {
            this.server = server;
            this.port = server.port();
            this.owner = owner;
        }

        static Env start() throws Exception {
            Server server = new Server(new Accounts(null, null), null, Options.DEFAULT, "127.0.0.1", 0);
            server.start();
            return new Env(server, true);
        }

        /** Клиент для уже запущенного сервера; close() его останавливает. */
        static Env on(Server server) {
            return new Env(server, true);
        }

        /** Второй пользователь того же сервера: свой набор cookie. */
        Env anotherUser() {
            return new Env(server, false);
        }

        int port() {
            return port;
        }

        HttpResponse<String> call(String method, String path, String body, String... headers) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (body != null && headers.length == 0) b.header("Content-Type", "application/json");
            if (headers.length > 0) b.headers(headers);
            if (cookie != null) b.header("Cookie", cookie);
            HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
            for (String c : r.headers().allValues("Set-Cookie")) {
                String pair = c.substring(0, c.indexOf(';'));
                cookie = pair.endsWith("=") ? null : pair;
            }
            return r;
        }

        Map<String, Object> json(String method, String path, String body) throws Exception {
            HttpResponse<String> r = call(method, path, body);
            if (r.statusCode() >= 300) throw new AssertionError(method + " " + path + " → " + r.statusCode() + " " + r.body());
            return Json.parseObject(r.body());
        }

        void register(String email) throws Exception {
            eq(201, call("POST", "/api/auth/register",
                    "{\"email\":\"" + email + "\",\"password\":\"пароль-для-теста\"}").statusCode());
        }

        @Override
        public void close() {
            if (owner) server.stop();
        }
    }

    static void apiRequiresLogin() throws Exception {
        try (Env env = Env.start()) {
            eq(401, env.call("GET", "/api/profile", null).statusCode());
            eq(401, env.call("GET", "/api/auth/me", null).statusCode());
            eq(200, env.call("GET", "/api/rules", null).statusCode()); // справочник открыт
            env.register("me@example.am");
            eq(200, env.call("GET", "/api/profile", null).statusCode());
            eq(204, env.call("POST", "/api/auth/logout", null).statusCode());
            eq(401, env.call("GET", "/api/profile", null).statusCode());
            eq(401, env.call("POST", "/api/auth/login", "{\"email\":\"me@example.am\",\"password\":\"не тот\"}").statusCode());
            eq(200, env.call("POST", "/api/auth/login", "{\"email\":\"me@example.am\",\"password\":\"пароль-для-теста\"}").statusCode());
            eq(200, env.call("GET", "/api/profile", null).statusCode());
        }
    }

    static void apiFlow() throws Exception {
        try (Env env = Env.start()) {
            env.register("flow@example.am");
            int year = LocalDate.now(Main.ZONE).getYear();
            eq(200, env.call("PUT", "/api/profile",
                    "{\"name\":\"Тест\",\"regime\":\"TURNOVER\",\"activity\":\"SERVICES\"}").statusCode());

            HttpResponse<String> created = env.call("POST", "/api/invoices",
                    "{\"client\":\"ООО Клиент\",\"date\":\"" + year + "-02-10\","
                            + "\"items\":[{\"name\":\"Разработка\",\"quantity\":2,\"price\":500000}]}");
            eq(201, created.statusCode());
            Map<String, Object> inv = Json.parseObject(created.body());
            eq("PT-" + year + "-001", inv.get("number"));
            eq(d("1000000"), inv.get("total"));

            Map<String, Object> paid = env.json("POST", "/api/invoices/" + inv.get("id") + "/paid?date=" + year + "-02-15", null);
            eq("PAID", paid.get("status"));
            Map<String, Object> e = env.json("PUT", "/api/invoices/" + inv.get("id") + "/einvoice", "{\"eInvoiceNumber\":\"A1234567\"}");
            eq("A1234567", e.get("eInvoiceNumber"));

            Map<String, Object> summary = env.json("GET", "/api/summary?year=" + year, null);
            eq(d("1000000"), summary.get("totalIncome"));
            eq(d("100000"), summary.get("totalTax"));
            eq(d("100000"), summary.get("totalDue"));

            eq(201, env.call("POST", "/api/payments",
                    "{\"year\":" + year + ",\"quarter\":1,\"date\":\"" + year + "-04-10\",\"amount\":\"100000\"}").statusCode());
            summary = env.json("GET", "/api/summary?year=" + year, null);
            eq(d("0"), summary.get("totalDue"));
            eq(d("100000"), summary.get("totalPaid"));
            Map<?, ?> pay = (Map<?, ?>) ((List<?>) Json.parse(env.call("GET", "/api/payments?year=" + year, null).body())).get(0);
            eq(204, env.call("DELETE", "/api/payments/" + pay.get("id"), null).statusCode());
            eq(d("100000"), env.json("GET", "/api/summary?year=" + year, null).get("totalDue"));

            eq(true, env.call("GET", "/", null).body().contains("pulsar-tax-api"));
            eq(404, env.call("GET", "/index.html", null).statusCode()); // фронтенд в отдельном репозитории
        }
    }

    static void apiIsolation() throws Exception {
        try (Env a = Env.start()) {
            a.register("a@example.am");
            Map<String, Object> inv = a.json("POST", "/api/invoices",
                    "{\"client\":\"X\",\"items\":[{\"name\":\"n\",\"quantity\":1,\"price\":10}]}");
            Env b = a.anotherUser();
            b.register("b@example.am");
            eq(404, b.call("GET", "/api/invoices/" + inv.get("id"), null).statusCode());
            eq(404, b.call("DELETE", "/api/invoices/" + inv.get("id"), null).statusCode());
            eq("[]", b.call("GET", "/api/invoices", null).body());
            eq(200, a.call("GET", "/api/invoices/" + inv.get("id"), null).statusCode());
        }
    }

    static void apiValidation() throws Exception {
        try (Env env = Env.start()) {
            env.register("v@example.am");
            HttpResponse<String> r = env.call("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":-5}");
            eq(400, r.statusCode());
            eq(true, Json.parseObject(r.body()).containsKey("error"));
            eq(400, env.call("POST", "/api/incomes", "не json").statusCode());
            eq(400, env.call("POST", "/api/invoices", "{\"client\":\"X\",\"items\":[]}").statusCode());
            eq(400, env.call("PUT", "/api/profile", "{\"regime\":\"TURNOVER\",\"activity\":\"CUSTOM\"}").statusCode());
            eq(400, env.call("POST", "/api/payments", "{\"year\":2026,\"quarter\":5,\"date\":\"2026-01-01\",\"amount\":1}").statusCode());
            eq(404, env.call("DELETE", "/api/incomes/999", null).statusCode());
            eq(404, env.call("GET", "/api/unknown", null).statusCode());
            eq(400, env.call("GET", "/api/summary?year=abc", null).statusCode());
            eq(400, env.call("PUT", "/api/account", "{\"lang\":\"fr\"}").statusCode());
            eq(409, env.call("POST", "/api/auth/register", "{\"email\":\"v@example.am\",\"password\":\"пароль-для-теста\"}").statusCode());
        }
    }

    static void apiRequestSource() throws Exception {
        try (Env env = Env.start()) {
            env.register("s@example.am");
            String income = "{\"date\":\"2026-01-01\",\"amount\":5}";
            // Простой запрос с чужого сайта: text/plain без preflight.
            eq(415, env.call("POST", "/api/incomes", income, "Content-Type", "text/plain").statusCode());
            eq(403, env.call("POST", "/api/incomes", income, "Content-Type", "application/json",
                    "Origin", "https://evil.example").statusCode());
            eq(403, env.call("POST", "/api/invoices/1/paid", null, "Origin", "https://evil.example").statusCode());
            eq(201, env.call("POST", "/api/incomes", income, "Content-Type", "application/json",
                    "Origin", "http://127.0.0.1:" + env.port()).statusCode());
            eq(400, env.call("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":\"1e999999999\"}").statusCode());
            // Подменённое имя (DNS rebinding) видно по заголовку Host.
            eq("HTTP/1.1 403", rawStatus(env.port(), "evil.example:" + env.port()));
            eq("HTTP/1.1 401", rawStatus(env.port(), "localhost:" + env.port()));
        }
    }

    /** HttpClient не даёт задать Host, поэтому запрос пишем в сокет сами. Возвращает строку статуса. */
    private static String rawStatus(int port, String host) throws Exception {
        try (Socket sock = new Socket("127.0.0.1", port)) {
            sock.getOutputStream().write(("GET /api/profile HTTP/1.1\r\nHost: " + host
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(sock.getInputStream().readNBytes(12), StandardCharsets.US_ASCII);
        }
    }

    static void apiMisc() throws Exception {
        try (Env env = Env.start()) {
            Map<String, Object> rules = env.json("GET", "/api/rules?year=2026", null);
            eq(d("24000000"), rules.get("microThreshold"));
            eq(ActivityType.values().length, ((List<?>) rules.get("activities")).size());

            env.register("misc@example.am");
            eq(200, env.call("PUT", "/api/profile",
                    "{\"name\":\"Тест\",\"regime\":\"TURNOVER\",\"activity\":\"DESIGN\"}").statusCode());

            String batch = "{\"items\":[{\"date\":\"2026-03-01\",\"amount\":\"1000\",\"importKey\":\"k1\"},"
                    + "{\"date\":\"2026-03-02\",\"currency\":\"USD\",\"originalAmount\":\"10\",\"rate\":\"390\",\"importKey\":\"k2\"}]}";
            Map<String, Object> r = env.json("POST", "/api/incomes/import", batch);
            eq(d("2"), r.get("added"));
            r = env.json("POST", "/api/incomes/import", batch);
            eq(d("0"), r.get("added"));
            eq(d("2"), r.get("skipped"));
            HttpResponse<String> bad = env.call("POST", "/api/incomes/import", "{\"items\":[{\"date\":\"2026-03-01\",\"amount\":\"-1\"}]}");
            eq(400, bad.statusCode());
            eq(true, bad.body().contains("Строка 1"));

            Map<String, Object> summary = env.json("GET", "/api/summary?year=2026", null);
            eq(d("4900"), summary.get("totalIncome"));
            eq(true, summary.get("regimeAllowed"));

            HttpResponse<String> csv = env.call("GET", "/api/export/incomes.csv", null);
            eq(true, csv.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
            eq(true, csv.body().contains("USD"));
            eq(true, env.call("GET", "/api/export/data.json", null).body().contains("\"version\":2"));

            Map<String, Object> me = env.json("GET", "/api/auth/me", null);
            eq(false, me.get("telegramEnabled"));
            eq(400, env.call("POST", "/api/account/telegram", null).statusCode());
            HttpResponse<String> ics = env.call("GET", (String) me.get("calendarPath"), null);
            eq(200, ics.statusCode());
            eq(true, ics.body().contains("BEGIN:VCALENDAR"));
            eq(404, env.call("GET", "/cal/" + "0".repeat(40) + ".ics", null).statusCode());

            eq("en", env.json("PUT", "/api/account", "{\"lang\":\"en\"}").get("lang"));
        }
    }

    static void apiCors() throws Exception {
        Options opts = new Options(false, true, "Strict", java.util.Set.of("https://app.pulsar.am"));
        Server server = new Server(new Accounts(null, null), null, opts, "127.0.0.1", 0);
        server.start();
        try (Env env = Env.on(server)) {
            // Предварительный запрос с разрешённого адреса.
            HttpResponse<String> pre = env.call("OPTIONS", "/api/incomes", null, "Origin", "https://app.pulsar.am",
                    "Access-Control-Request-Method", "POST", "Access-Control-Request-Headers", "content-type");
            eq(204, pre.statusCode());
            eq("https://app.pulsar.am", pre.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
            eq("true", pre.headers().firstValue("Access-Control-Allow-Credentials").orElse(""));
            eq(true, pre.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("DELETE"));
            // Чужой адрес не получает разрешения.
            eq(403, env.call("OPTIONS", "/api/incomes", null, "Origin", "https://evil.example").statusCode());
            HttpResponse<String> get = env.call("GET", "/api/rules", null, "Origin", "https://evil.example");
            eq(200, get.statusCode());
            eq(true, get.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            // Фронтенд при разработке на другом локальном порту.
            eq("http://localhost:5500", env.call("GET", "/api/rules", null, "Origin", "http://localhost:5500")
                    .headers().firstValue("Access-Control-Allow-Origin").orElse(""));
            // Регистрация с разрешённого адреса проходит, с чужого — нет.
            String reg = "{\"email\":\"c@example.am\",\"password\":\"пароль-для-теста\"}";
            eq(403, env.call("POST", "/api/auth/register", reg, "Content-Type", "application/json",
                    "Origin", "https://evil.example").statusCode());
            HttpResponse<String> ok = env.call("POST", "/api/auth/register", reg, "Content-Type", "application/json",
                    "Origin", "https://app.pulsar.am");
            eq(201, ok.statusCode());
            eq("https://app.pulsar.am", ok.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        }

        // На сетевом адресе локальные адреса без списка не разрешены.
        Server open = new Server(new Accounts(null, null), null, Options.DEFAULT, "0.0.0.0", 0);
        eq(false, open.originAllowed("http://localhost:5500", "api.pulsar.am"));
        eq(true, open.originAllowed("https://api.pulsar.am", "api.pulsar.am"));
    }

    static void cookieOptions() throws Exception {
        expectIllegal(() -> new Options(false, true, "None", java.util.Set.of())); // None без Secure браузер отвергнет
        expectIllegal(() -> new Options(true, true, "Whatever", java.util.Set.of()));
        Options cross = new Options(true, true, "None", java.util.Set.of("https://pulsar-tax-web.vercel.app"));
        Server server = new Server(new Accounts(null, null), null, cross, "127.0.0.1", 0);
        server.start();
        try (Env env = Env.on(server)) {
            HttpResponse<String> r = env.call("POST", "/api/auth/register",
                    "{\"email\":\"x@example.am\",\"password\":\"пароль-для-теста\"}");
            String cookie = r.headers().firstValue("Set-Cookie").orElse("");
            eq(true, cookie.contains("SameSite=None") && cookie.contains("Secure") && cookie.contains("HttpOnly"));
        }
    }

    private static void expectIllegal(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("ожидалась IllegalArgumentException");
    }

    static void apiBearerToken() throws Exception {
        try (Env env = Env.start()) {
            HttpResponse<String> reg = env.call("POST", "/api/auth/register",
                    "{\"email\":\"app@example.am\",\"password\":\"пароль-для-теста\",\"tokenAuth\":true}");
            eq(201, reg.statusCode());
            eq(true, reg.headers().firstValue("Set-Cookie").isEmpty()); // приложению cookie не нужна
            String token = (String) Json.parseObject(reg.body()).get("token");
            eq(64, token.length());

            Env app = env.anotherUser(); // без cookie
            eq(401, app.call("GET", "/api/profile", null).statusCode());
            String auth = "Bearer " + token;
            eq(200, app.call("GET", "/api/profile", null, "Authorization", auth).statusCode());
            eq(201, app.call("POST", "/api/incomes", "{\"date\":\"2026-01-01\",\"amount\":5}",
                    "Content-Type", "application/json", "Authorization", auth).statusCode());
            eq(401, app.call("GET", "/api/profile", null, "Authorization", "Bearer " + "0".repeat(64)).statusCode());

            // Вход по паролю тоже отдаёт токен; смена пароля — новый токен, старый перестаёт работать.
            HttpResponse<String> login = app.call("POST", "/api/auth/login",
                    "{\"email\":\"app@example.am\",\"password\":\"пароль-для-теста\",\"tokenAuth\":true}");
            String token2 = (String) Json.parseObject(login.body()).get("token");
            HttpResponse<String> pw = app.call("POST", "/api/account/password",
                    "{\"oldPassword\":\"пароль-для-теста\",\"newPassword\":\"новый-пароль-1\"}",
                    "Content-Type", "application/json", "Authorization", "Bearer " + token2);
            String token3 = (String) Json.parseObject(pw.body()).get("token");
            eq(true, token3 != null && !token3.equals(token2));
            eq(401, app.call("GET", "/api/profile", null, "Authorization", "Bearer " + token2).statusCode());
            eq(200, app.call("GET", "/api/profile", null, "Authorization", "Bearer " + token3).statusCode());
            eq(204, app.call("POST", "/api/auth/logout", null, "Authorization", "Bearer " + token3).statusCode());
            eq(401, app.call("GET", "/api/profile", null, "Authorization", "Bearer " + token3).statusCode());
        }
    }

    /** Ответ ЦБ РА в том виде, как его отдаёт метод ExchangeRatesByDate (сокращённо). */
    static final String CBA_RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>
            <ExchangeRatesByDateResponse xmlns="http://www.cba.am/"><ExchangeRatesByDateResult>
            <CurrentDate>2026-10-09T00:00:00</CurrentDate><PreviousDate>2026-10-08T00:00:00</PreviousDate>
            <Rates>
              <ExchangeRate><ISO>USD</ISO><Amount>1</Amount><Rate>387.45</Rate><Difference>-0.3</Difference></ExchangeRate>
              <ExchangeRate><ISO>EUR</ISO><Amount>1</Amount><Rate>451.2</Rate><Difference>0.1</Difference></ExchangeRate>
              <ExchangeRate><ISO>JPY</ISO><Amount>10</Amount><Rate>26.1</Rate><Difference>0</Difference></ExchangeRate>
            </Rates></ExchangeRatesByDateResult></ExchangeRatesByDateResponse></soap:Body></soap:Envelope>""";

    static void ratesParse() {
        Map<String, BigDecimal> r = Rates.parse(CBA_RESPONSE);
        eq(d("387.45"), r.get("USD"));
        eq(d("451.2"), r.get("EUR"));
        eq(d("2.61"), r.get("JPY")); // курс за 10 иен делится на 10
        expectStatus(503, () -> Rates.parse("<html>ошибка</html>"));
    }

    static void apiExpensesProfilesRates() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> lastBody = new java.util.concurrent.atomic.AtomicReference<>();
        com.sun.net.httpserver.HttpServer cba = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        cba.createContext("/", ex -> {
            calls.incrementAndGet();
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = CBA_RESPONSE.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        cba.start();
        Options opts = new Options(false, true, "Strict", java.util.Set.of(),
                "http://127.0.0.1:" + cba.getAddress().getPort() + "/exchangerates.asmx");
        Server server = new Server(new Accounts(null, null), null, opts, "127.0.0.1", 0);
        server.start();
        try (Env env = Env.on(server)) {
            env.register("money@example.am");

            // Расходы: учёт и сводка, на налог не влияют.
            eq(201, env.call("POST", "/api/expenses",
                    "{\"date\":\"2026-02-10\",\"amount\":\"12000\",\"category\":\"SOFTWARE\",\"description\":\"Figma\"}").statusCode());
            eq(201, env.call("POST", "/api/expenses", "{\"date\":\"2026-05-01\",\"amount\":\"3000\"}").statusCode());
            eq(400, env.call("POST", "/api/expenses", "{\"date\":\"2026-05-01\",\"amount\":\"-1\"}").statusCode());
            eq(400, env.call("POST", "/api/expenses", "{\"date\":\"2026-05-01\",\"amount\":\"1\",\"category\":\"FOOD\"}").statusCode());
            List<?> list = (List<?>) Json.parse(env.call("GET", "/api/expenses?year=2026", null).body());
            eq(2, list.size());
            eq("OTHER", ((Map<?, ?>) list.get(0)).get("category"));
            env.json("PUT", "/api/profile", "{\"name\":\"Т\",\"regime\":\"TURNOVER\",\"activity\":\"SERVICES\"}");
            env.call("POST", "/api/incomes", "{\"date\":\"2026-02-01\",\"amount\":\"100000\"}");
            Map<String, Object> s = env.json("GET", "/api/summary?year=2026", null);
            eq(d("15000"), s.get("totalExpenses"));
            eq(d("10000"), s.get("totalTax")); // расходы не уменьшают налог
            eq(d("10"), s.get("setAsidePercent"));
            eq(d("12000"), ((Map<?, ?>) ((List<?>) s.get("quarters")).get(0)).get("expenses"));
            eq(204, env.call("DELETE", "/api/expenses/" + ((Map<?, ?>) list.get(0)).get("id"), null).statusCode()); // новее — первым
            eq(404, env.call("DELETE", "/api/expenses/999999", null).statusCode());
            eq(true, env.call("GET", "/api/export/expenses.csv", null).body().contains("SOFTWARE;Figma"));

            // Настройки импорта: сохраняются по отпечатку заголовка и проверяются.
            String mapping = "{\"date\":0,\"amount\":1,\"debit\":2,\"desc\":4,\"currencyCol\":-1,"
                    + "\"dateFormat\":\"dd.mm.yyyy\",\"currency\":\"AMD\"}";
            eq(204, env.call("PUT", "/api/import-profiles/csv-1a2b3c4d", mapping).statusCode());
            eq(400, env.call("PUT", "/api/import-profiles/csv-1a2b3c4d", mapping.replace("dd.mm.yyyy", "dd-mm")).statusCode());
            eq(400, env.call("PUT", "/api/import-profiles/csv-1a2b3c4d", mapping.replace("\"date\":0", "\"date\":-5")).statusCode());
            eq(404, env.call("PUT", "/api/import-profiles/BAD!", mapping).statusCode());
            Map<String, Object> profiles = env.json("GET", "/api/import-profiles", null);
            eq(d("4"), ((Map<?, ?>) profiles.get("csv-1a2b3c4d")).get("desc"));

            // Курсы ЦБ: запрос на нужную дату, прошедшие даты кэшируются.
            Map<String, Object> r = env.json("GET", "/api/rates?date=2026-10-09", null);
            eq(d("387.45"), ((Map<?, ?>) r.get("rates")).get("USD"));
            eq(true, lastBody.get().contains("<date>2026-10-09T00:00:00</date>"));
            env.json("GET", "/api/rates?date=2026-10-09", null);
            eq(1, calls.get());
            eq(400, env.call("GET", "/api/rates?date=2999-01-01", null).statusCode());
            eq(400, env.call("GET", "/api/rates?date=вчера", null).statusCode());
        } finally {
            cba.stop(0);
        }

        // ЦБ недоступен — понятная ошибка, а не 500.
        Server offline = new Server(new Accounts(null, null), null,
                new Options(false, true, "Strict", java.util.Set.of(), "http://127.0.0.1:1/none"), "127.0.0.1", 0);
        offline.start();
        try (Env env = Env.on(offline)) {
            env.register("offline@example.am");
            HttpResponse<String> res = env.call("GET", "/api/rates?date=2026-10-09", null);
            eq(503, res.statusCode());
            eq(true, res.body().contains("вручную"));
            eq(401, env.anotherUser().call("GET", "/api/rates", null).statusCode()); // только после входа
        }
    }

    static void storeExpensesProfiles() throws Exception {
        Path file = Files.createTempDirectory("pulsar-exp").resolve("data.json");
        Store s = new Store(file);
        Expense e = Codec.expense(Map.of("date", "2026-03-01", "amount", "500", "category", "RENT"));
        s.addExpense(e);
        s.saveImportProfile("csv-aaaa", Codec.importProfile(Map.of("date", d("0"), "amount", d("1"), "debit", d("-1"),
                "desc", d("2"), "currencyCol", d("-1"), "dateFormat", "yyyy-mm-dd", "currency", "USD")));
        for (int i = 0; i < 25; i++) {
            s.saveImportProfile("csv-p" + i, Codec.importProfile(Map.of("date", d("0"), "amount", d("1"), "debit", d("-1"),
                    "desc", d("-1"), "currencyCol", d("-1"), "dateFormat", "dd.mm.yyyy", "currency", "AMD")));
        }
        Store reloaded = new Store(file);
        eq(1, reloaded.expenses(2026).size());
        eq(ExpenseCategory.RENT, reloaded.expenses(2026).get(0).category);
        eq(20, reloaded.importProfiles().size()); // старые настройки вытесняются
        eq(false, reloaded.importProfiles().containsKey("csv-aaaa"));
        eq(true, reloaded.addIncome(income("2026-03-02", "1", null)).id > e.id);
    }
}
