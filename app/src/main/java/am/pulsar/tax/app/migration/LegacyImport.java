package am.pulsar.tax.app.migration;import am.pulsar.tax.app.config.PulsarProperties;
import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.ImportProfile;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.entity.InvoiceItem;
import am.pulsar.tax.app.entity.Session;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.entity.User;
import am.pulsar.tax.app.repository.BusinessRepository;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.repository.ImportProfileRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.InvoiceRepository;
import am.pulsar.tax.app.repository.SessionRepository;
import am.pulsar.tax.app.repository.TaxPaymentRepository;
import am.pulsar.tax.app.repository.UserRepository;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.provider.BusinessProvider;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import am.pulsar.tax.dto.enums.InvoiceStatus;
import am.pulsar.tax.dto.patch.BusinessPatchDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LegacyImport implements ApplicationRunner {

    PulsarProperties props;
    JsonMapper json;
    UserRepository userRepository;
    SessionRepository sessionRepository;
    BusinessService businessService;
    BusinessProvider businessProvider;
    BusinessRepository businessRepository;
    IncomeRepository incomeRepository;
    InvoiceRepository invoiceRepository;
    ExpenseRepository expenseRepository;
    TaxPaymentRepository taxPaymentRepository;
    ImportProfileRepository importProfileRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        String dir = props.importLegacy();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path root = Path.of(dir);
        Path usersFile = root.resolve("users.json");
        if (!Files.exists(usersFile)) {
            throw new IllegalStateException("Нет файла " + usersFile.toAbsolutePath());
        }
        if (userRepository.count() > 0) {
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
        User user = userRepository.save(User.builder()
                .email(u.path("email").asString())
                .passwordSalt(u.path("salt").asString())
                .passwordHash(u.path("hash").asString())
                .calendarToken(u.path("calendarToken").asString())
                .telegramChatId(u.path("telegramChatId").isNumber() ? u.path("telegramChatId").asLong() : null)
                .lang(u.path("lang").isString() ? u.path("lang").asString() : "ru")
                .lastReminder(date(u.path("lastReminder")))
                .build());
        Instant now = Instant.now();
        for (Map.Entry<String, JsonNode> s : u.path("sessions").properties()) {
            Instant until = Instant.ofEpochSecond(s.getValue().asLong());
            if (until.isAfter(now)) {
                sessionRepository.save(Session.builder().tokenHash(s.getKey()).userId(user.getId()).expiresAt(until).build());
            }
        }

        Path file = root.resolve("users").resolve(u.path("id").asLong() + ".json");
        if (!Files.exists(file)) {
            return;
        }
        JsonNode data = json.readTree(Files.readString(file));
        Business business = businessService.getOrCreateForUser(user.getId());
        if (data.path("profile").isObject()) {
            businessProvider.patch(json.treeToValue(data.path("profile"), BusinessPatchDto.class), business);
            businessRepository.save(business);
        }
        Long bid = business.getId();

        Map<Long, Long> invoiceIds = new HashMap<>();
        for (JsonNode n : data.path("invoices")) {
            List<InvoiceItem> items = new ArrayList<>();
            for (JsonNode it : n.path("items")) {
                items.add(new InvoiceItem(it.path("name").asString(), dec(it.path("quantity")), dec(it.path("price"))));
            }
            Invoice invoice = invoiceRepository.save(Invoice.builder()
                    .businessId(bid)
                    .number(n.path("number").asString())
                    .date(LocalDate.parse(n.path("date").asString()))
                    .dueDate(date(n.path("dueDate")))
                    .client(n.path("client").asString())
                    .clientTin(n.path("clientTin").asString(""))
                    .eInvoiceNumber(n.path("eInvoiceNumber").asString(""))
                    .status(InvoiceStatus.valueOf(n.path("status").asString("SENT")))
                    .items(items)
                    .build());
            invoiceIds.put(n.path("id").asLong(), invoice.getId());
        }
        for (JsonNode n : data.path("incomes")) {
            incomeRepository.save(Income.builder()
                    .businessId(bid)
                    .date(LocalDate.parse(n.path("date").asString()))
                    .amount(dec(n.path("amount")).setScale(2, RoundingMode.HALF_UP))
                    .currency(Currency.valueOf(n.path("currency").asString("AMD")))
                    .originalAmount(decOrNull(n.path("originalAmount")))
                    .rate(decOrNull(n.path("rate")))
                    .client(n.path("client").asString(""))
                    .description(n.path("description").asString(""))
                    .invoiceId(n.path("invoiceId").isNumber() ? invoiceIds.get(n.path("invoiceId").asLong()) : null)
                    .importKey(n.path("importKey").isString() ? n.path("importKey").asString() : null)
                    .build());
        }
        for (JsonNode n : data.path("expenses")) {
            expenseRepository.save(Expense.builder()
                    .businessId(bid)
                    .date(LocalDate.parse(n.path("date").asString()))
                    .amount(dec(n.path("amount")))
                    .category(ExpenseCategory.valueOf(n.path("category").asString("OTHER")))
                    .description(n.path("description").asString(""))
                    .build());
        }
        for (JsonNode n : data.path("payments")) {
            taxPaymentRepository.save(TaxPayment.builder()
                    .businessId(bid)
                    .year(n.path("year").asInt())
                    .quarter(n.path("quarter").asInt())
                    .date(LocalDate.parse(n.path("date").asString()))
                    .amount(dec(n.path("amount")))
                    .build());
        }
        for (Map.Entry<String, JsonNode> p : data.path("importProfiles").properties()) {
            importProfileRepository.save(ImportProfile.builder()
                    .businessId(bid)
                    .signature(p.getKey())
                    .mapping(json.writeValueAsString(p.getValue()))
                    .updatedAt(ClockUtils.now())
                    .build());
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
