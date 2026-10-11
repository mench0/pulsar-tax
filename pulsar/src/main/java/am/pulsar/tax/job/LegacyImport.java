package am.pulsar.tax.job;

import am.pulsar.tax.config.PulsarProperties;
import am.pulsar.tax.entity.Business;
import am.pulsar.tax.entity.Enums.Currency;
import am.pulsar.tax.entity.Enums.ExpenseCategory;
import am.pulsar.tax.entity.Enums.InvoiceStatus;
import am.pulsar.tax.entity.Expense;
import am.pulsar.tax.entity.ImportProfile;
import am.pulsar.tax.entity.Income;
import am.pulsar.tax.entity.Invoice;
import am.pulsar.tax.entity.InvoiceItem;
import am.pulsar.tax.entity.Session;
import am.pulsar.tax.entity.TaxPayment;
import am.pulsar.tax.entity.User;
import am.pulsar.tax.repository.ExpenseRepository;
import am.pulsar.tax.repository.ImportProfileRepository;
import am.pulsar.tax.repository.IncomeRepository;
import am.pulsar.tax.repository.InvoiceRepository;
import am.pulsar.tax.repository.SessionRepository;
import am.pulsar.tax.repository.TaxPaymentRepository;
import am.pulsar.tax.repository.UserRepository;
import am.pulsar.tax.service.BusinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Перенос данных версии на файлах (users.json и users/{id}.json) в базу. Запускается, если задан
 * PULSAR_IMPORT_LEGACY и в базе ещё нет пользователей. Пароли и сессии переносятся как есть:
 * формат хэшей не изменился, поэтому заново входить не нужно.
 */
@Component
public class LegacyImport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyImport.class);

    private final PulsarProperties props;
    private final JsonMapper json;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final BusinessService businesses;
    private final IncomeRepository incomes;
    private final InvoiceRepository invoices;
    private final ExpenseRepository expenses;
    private final TaxPaymentRepository payments;
    private final ImportProfileRepository importProfiles;

    public LegacyImport(PulsarProperties props, JsonMapper json, UserRepository users, SessionRepository sessions,
                        BusinessService businesses, IncomeRepository incomes, InvoiceRepository invoices,
                        ExpenseRepository expenses, TaxPaymentRepository payments,
                        ImportProfileRepository importProfiles) {
        this.props = props;
        this.json = json;
        this.users = users;
        this.sessions = sessions;
        this.businesses = businesses;
        this.incomes = incomes;
        this.invoices = invoices;
        this.expenses = expenses;
        this.payments = payments;
        this.importProfiles = importProfiles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        String dir = props.importLegacy();
        if (dir == null || dir.isBlank()) return;
        Path root = Path.of(dir);
        Path usersFile = root.resolve("users.json");
        if (!Files.exists(usersFile)) throw new IllegalStateException("Нет файла " + usersFile.toAbsolutePath());
        if (users.count() > 0) {
            log.info("Перенос старых данных пропущен: в базе уже есть пользователи");
            return;
        }
        int count = 0;
        for (JsonNode u : json.readTree(Files.readString(usersFile)).path("users")) {
            importUser(root, u);
            count++;
        }
        log.info("Перенесено пользователей из {}: {}", root.toAbsolutePath(), count);
    }

    private void importUser(Path root, JsonNode u) throws Exception {
        User user = new User(u.path("email").asString(), u.path("salt").asString(), u.path("hash").asString(),
                u.path("calendarToken").asString());
        if (u.path("telegramChatId").isNumber()) user.setTelegramChatId(u.path("telegramChatId").asLong());
        if (u.path("lang").isString()) user.setLang(u.path("lang").asString());
        if (u.path("lastReminder").isString()) user.setLastReminder(LocalDate.parse(u.path("lastReminder").asString()));
        users.save(user);
        Instant now = Instant.now();
        for (Map.Entry<String, JsonNode> s : u.path("sessions").properties()) {
            Instant until = Instant.ofEpochSecond(s.getValue().asLong());
            if (until.isAfter(now)) sessions.save(new Session(s.getKey(), user.getId(), until));
        }

        Path file = root.resolve("users").resolve(u.path("id").asLong() + ".json");
        if (!Files.exists(file)) return;
        JsonNode data = json.readTree(Files.readString(file));
        Business b = businesses.forUser(user.getId());
        if (data.path("profile").isObject()) {
            businesses.update(user.getId(), json.convertValue(data.path("profile"), Map.class));
        }
        long bid = b.getId();

        Map<Long, Long> invoiceIds = new HashMap<>();
        for (JsonNode n : data.path("invoices")) {
            List<InvoiceItem> items = new ArrayList<>();
            for (JsonNode it : n.path("items")) {
                items.add(new InvoiceItem(it.path("name").asString(), dec(it.path("quantity")), dec(it.path("price"))));
            }
            Invoice inv = new Invoice(bid, n.path("number").asString(), LocalDate.parse(n.path("date").asString()),
                    date(n.path("dueDate")), n.path("client").asString(), n.path("clientTin").asString(""),
                    n.path("eInvoiceNumber").asString(""), items);
            inv.setStatus(InvoiceStatus.valueOf(n.path("status").asString("SENT")));
            invoices.save(inv);
            invoiceIds.put(n.path("id").asLong(), inv.getId());
        }
        for (JsonNode n : data.path("incomes")) {
            Long invoiceId = n.path("invoiceId").isNumber() ? invoiceIds.get(n.path("invoiceId").asLong()) : null;
            incomes.save(new Income(bid, LocalDate.parse(n.path("date").asString()),
                    dec(n.path("amount")).setScale(2, RoundingMode.HALF_UP),
                    Currency.valueOf(n.path("currency").asString("AMD")),
                    decOrNull(n.path("originalAmount")), decOrNull(n.path("rate")),
                    n.path("client").asString(""), n.path("description").asString(""), invoiceId,
                    n.path("importKey").isString() ? n.path("importKey").asString() : null));
        }
        for (JsonNode n : data.path("expenses")) {
            expenses.save(new Expense(bid, LocalDate.parse(n.path("date").asString()), dec(n.path("amount")),
                    ExpenseCategory.valueOf(n.path("category").asString("OTHER")), n.path("description").asString("")));
        }
        for (JsonNode n : data.path("payments")) {
            payments.save(new TaxPayment(bid, n.path("year").asInt(), n.path("quarter").asInt(),
                    LocalDate.parse(n.path("date").asString()), dec(n.path("amount"))));
        }
        for (Map.Entry<String, JsonNode> p : data.path("importProfiles").properties()) {
            ImportProfile profile = new ImportProfile(bid, p.getKey());
            profile.setMapping(json.writeValueAsString(p.getValue()), now);
            importProfiles.save(profile);
        }
    }

    private static BigDecimal dec(JsonNode n) {
        return n.isNumber() ? n.decimalValue() : new BigDecimal(n.asString());
    }

    private static BigDecimal decOrNull(JsonNode n) {
        return n.isNull() || n.isMissingNode() ? null : dec(n);
    }

    private static LocalDate date(JsonNode n) {
        return n.isString() && !n.asString().isEmpty() ? LocalDate.parse(n.asString()) : null;
    }
}
