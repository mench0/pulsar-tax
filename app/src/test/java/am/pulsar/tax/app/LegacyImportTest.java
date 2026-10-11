package am.pulsar.tax.app;import am.pulsar.tax.app.utils.Passwords;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перенос папки данных версии на файлах: пароль, сессия и данные ИП переживают переход на PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {PulsarApplication.class, TestDatabaseConfig.class},
        properties = {
        // Переменные PULSAR_* на машине разработчика не должны менять поведение тестов.
        "spring.flyway.placeholders.first_user_email=", "spring.flyway.placeholders.first_user_password=",
        "pulsar.signup=open", "pulsar.secure-cookie=false", "pulsar.cookie-same-site=Strict",
        "pulsar.session.ttl=1h", "pulsar.session.store=caffeine"})
class LegacyImportTest {

    static final String SESSION = "ab".repeat(32);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        Path old = Files.createTempDirectory("pulsar-legacy");
        String salt = "0011223344556677";
        long until = Instant.now().getEpochSecond() + 86400;
        Files.writeString(old.resolve("users.json"), """
                {"version":1,"nextId":2,"users":[{"id":1,"email":"old@example.am","salt":"%s","hash":"%s",
                 "calendarToken":"%s","telegramChatId":null,"lang":"en","lastReminder":null,
                 "sessions":{"%s":%d}}]}""".formatted(salt, Passwords.hash("старый-пароль", salt), "c".repeat(40),
                Passwords.sha256(SESSION), until));
        Files.createDirectories(old.resolve("users"));
        Files.writeString(old.resolve("users").resolve("1.json"), """
                {"version":2,"nextId":10,
                 "profile":{"name":"Старый ИП","tin":"123","address":"","bankAccount":"","regime":"TURNOVER","activity":"SERVICES","customRate":null},
                 "incomes":[{"id":1,"date":"2026-02-01","amount":1000,"currency":"AMD","client":"","description":"","invoiceId":null,"importKey":"k1"},
                            {"id":3,"date":"2026-03-01","amount":2001,"currency":"AMD","client":"Б","description":"Оплата","invoiceId":2,"importKey":null},
                            {"id":4,"date":"2026-03-02","amount":38750,"currency":"USD","originalAmount":100,"rate":387.5,"client":"","description":"","invoiceId":null,"importKey":null}],
                 "invoices":[{"id":2,"number":"PT-2026-001","date":"2026-02-20","dueDate":null,"client":"Б","clientTin":"","status":"PAID","eInvoiceNumber":"E-7",
                              "items":[{"name":"Работа","quantity":2,"price":1000.4,"sum":2001}],"total":2001}],
                 "payments":[{"id":5,"year":2026,"quarter":1,"date":"2026-04-10","amount":300}],
                 "expenses":[{"id":6,"date":"2026-02-05","amount":50,"category":"RENT","description":"Офис"}],
                 "importProfiles":{"abcd":{"date":0,"amount":1,"debit":-1,"desc":2,"currencyCol":-1,"dateFormat":"dd.mm.yyyy","currency":"AMD"}}}""");
        r.add("pulsar.import-legacy", old::toString);
    }

    @LocalServerPort
    int port;

    @Test
    void oldDataIsImported() throws Exception {
        ApiClient c = new ApiClient(port);
        c.bearer = SESSION; // старая сессия действует
        assertEquals("en", c.json("GET", "/api/auth/me", null).path("lang").asString());
        assertEquals("/cal/" + "c".repeat(40) + ".ics", c.json("GET", "/api/auth/me", null).path("calendarPath").asString());

        ApiClient login = new ApiClient(port);
        assertEquals(200, login.status("POST", "/api/auth/login", "{\"email\":\"old@example.am\",\"password\":\"старый-пароль\"}"));
        assertEquals("Старый ИП", login.json("GET", "/api/profile", null).path("name").asString());
        JsonNode incomes = login.json("GET", "/api/incomes?year=2026", null);
        assertEquals(3, incomes.size());
        JsonNode invoice = login.json("GET", "/api/invoices", null).get(0);
        assertEquals("PAID", invoice.path("status").asString());
        assertEquals("E-7", invoice.path("eInvoiceNumber").asString());
        assertEquals(invoice.path("id").asLong(), incomes.get(1).path("invoiceId").asLong());
        assertEquals("387.5", incomes.get(0).path("rate").toString());
        JsonNode summary = login.json("GET", "/api/summary?year=2026", null);
        assertEquals("41751", summary.path("totalIncome").toString());
        assertEquals("300", summary.path("totalPaid").toString());
        assertEquals("50", summary.path("totalExpenses").toString());
        assertEquals(1, login.json("GET", "/api/import-profiles", null).size());
        // Повторный импорт той же строки выписки пропускается: importKey перенесён.
        assertEquals(1, login.json("POST", "/api/incomes/import",
                "{\"items\":[{\"date\":\"2026-02-01\",\"amount\":1000,\"importKey\":\"k1\"}]}").path("skipped").asInt());
    }
}
