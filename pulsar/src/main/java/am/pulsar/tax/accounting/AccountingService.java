package am.pulsar.tax.accounting;

import am.pulsar.tax.invoicing.InvoiceRepository;
import am.pulsar.tax.shared.ApiException;
import am.pulsar.tax.shared.Enums.Currency;
import am.pulsar.tax.shared.Enums.ExpenseCategory;
import am.pulsar.tax.shared.Enums.InvoiceStatus;
import am.pulsar.tax.shared.Fields;
import am.pulsar.tax.shared.Yerevan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Доходы, расходы и настройки импорта выписок одного ИП. */
@Service
public class AccountingService {

    public static final int MAX_IMPORT = 5000;
    private static final int MAX_IMPORT_PROFILES = 20;

    private final IncomeRepository incomes;
    private final ExpenseRepository expenses;
    private final ImportProfileRepository profiles;
    private final InvoiceRepository invoices;
    private final JsonMapper json;
    private final Yerevan yerevan;

    public AccountingService(IncomeRepository incomes, ExpenseRepository expenses, ImportProfileRepository profiles,
                             InvoiceRepository invoices, JsonMapper json, Yerevan yerevan) {
        this.incomes = incomes;
        this.expenses = expenses;
        this.profiles = profiles;
        this.invoices = invoices;
        this.json = json;
        this.yerevan = yerevan;
    }

    // ---------- Доходы ----------

    @Transactional(readOnly = true)
    public List<Income> incomes(long businessId, int year) {
        return incomes.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(businessId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public List<Income> allIncomes(long businessId) {
        return incomes.findByBusinessIdOrderByDateAscIdAsc(businessId);
    }

    @Transactional
    public Income addIncome(long businessId, Map<String, Object> body) {
        Income i = parseIncome(businessId, body);
        if (i.getImportKey() != null && incomes.importKeys(businessId).contains(i.getImportKey())) {
            throw new ApiException(409, "Эта строка выписки уже загружена");
        }
        return incomes.save(i);
    }

    /**
     * Доходы из выписки. Строки с уже известным importKey пропускаются, поэтому одну и ту же выписку
     * можно загрузить повторно.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> importIncomes(long businessId, Map<String, Object> body) {
        List<Object> raw = new Fields(body).list("items");
        if (raw.size() > MAX_IMPORT) throw ApiException.badRequest("За раз можно загрузить не больше " + MAX_IMPORT + " строк");
        List<Income> list = new ArrayList<>();
        for (int n = 0; n < raw.size(); n++) {
            if (!(raw.get(n) instanceof Map<?, ?> item)) throw ApiException.badRequest("Строка " + (n + 1) + ": неверный формат");
            try {
                list.add(parseIncome(businessId, (Map<String, Object>) item));
            } catch (ApiException e) {
                throw ApiException.badRequest("Строка " + (n + 1) + ": " + e.getMessage());
            }
        }
        Set<String> known = incomes.importKeys(businessId);
        List<Income> fresh = new ArrayList<>();
        int skipped = 0;
        for (Income i : list) {
            if (i.getImportKey() != null && !known.add(i.getImportKey())) {
                skipped++;
            } else {
                fresh.add(i);
            }
        }
        incomes.saveAll(fresh);
        return Map.of("added", fresh.size(), "skipped", skipped);
    }

    /** Удаляет доход; если он записан при оплате счёта, счёт снова становится неоплаченным. */
    @Transactional
    public void deleteIncome(long businessId, long id) {
        Income income = incomes.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Запись о доходе не найдена"));
        incomes.delete(income);
        if (income.getInvoiceId() != null) {
            invoices.findById(income.getInvoiceId()).ifPresent(inv -> inv.setStatus(InvoiceStatus.SENT));
        }
    }

    /**
     * Доход. В драмах передаётся amount; в другой валюте — originalAmount и rate (драмов за единицу),
     * а сумма в драмах считается здесь и округляется до драма.
     */
    static Income parseIncome(long businessId, Map<String, Object> json) {
        Fields f = new Fields(json);
        LocalDate date = f.date("date", true);
        Currency currency = f.enumOr("currency", Currency.class, Currency.AMD);
        BigDecimal amount;
        BigDecimal originalAmount = null;
        BigDecimal rate = null;
        if (currency == Currency.AMD) {
            amount = f.dec("amount", true);
        } else {
            originalAmount = f.dec("originalAmount", true);
            rate = f.dec("rate", true);
            if (originalAmount.signum() <= 0) throw ApiException.badRequest("Сумма в валюте должна быть больше нуля");
            if (rate.signum() <= 0) throw ApiException.badRequest("Курс должен быть больше нуля");
            amount = originalAmount.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        }
        if (amount.signum() <= 0) throw ApiException.badRequest("Сумма дохода должна быть больше нуля");
        if (amount.scale() > 2) throw ApiException.badRequest("Сумма дохода: не больше 2 знаков после запятой");
        if (amount.precision() - amount.scale() > 12) throw ApiException.badRequest("Сумма дохода слишком большая");
        String client = f.str("client", 200, false);
        String description = f.str("description", 500, false);
        String key = f.str("importKey", 100, false);
        return new Income(businessId, date, amount, currency, originalAmount, rate, client, description, null,
                key.isEmpty() ? null : key);
    }

    // ---------- Расходы ----------

    @Transactional(readOnly = true)
    public List<Expense> expenses(long businessId, int year) {
        return expenses.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(businessId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public List<Expense> allExpenses(long businessId) {
        return expenses.findByBusinessIdOrderByDateAscIdAsc(businessId);
    }

    @Transactional
    public Expense addExpense(long businessId, Map<String, Object> json) {
        Fields f = new Fields(json);
        LocalDate date = f.date("date", true);
        BigDecimal amount = f.dec("amount", true);
        if (amount.signum() <= 0) throw ApiException.badRequest("Сумма расхода должна быть больше нуля");
        if (amount.scale() > 2) throw ApiException.badRequest("Сумма расхода: не больше 2 знаков после запятой");
        ExpenseCategory category = f.enumOr("category", ExpenseCategory.class, ExpenseCategory.OTHER);
        return expenses.save(new Expense(businessId, date, amount, category, f.str("description", 500, false)));
    }

    @Transactional
    public void deleteExpense(long businessId, long id) {
        expenses.delete(expenses.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Запись о расходе не найдена")));
    }

    // ---------- Настройки импорта ----------

    @Transactional(readOnly = true)
    public Map<String, Object> importProfiles(long businessId) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ImportProfile p : profiles.findByBusinessIdOrderByUpdatedAtAsc(businessId)) {
            m.put(p.getSignature(), json.readValue(p.getMapping(), Map.class));
        }
        return m;
    }

    /** Сохраняет колонки выписки. Хватит с запасом на все банки; самые старые настройки уходят первыми. */
    @Transactional
    public void saveImportProfile(long businessId, String signature, Map<String, Object> body) {
        Map<String, Object> mapping = parseImportProfile(body);
        ImportProfile p = profiles.findById(new ImportProfile.Key(businessId, signature))
                .orElseGet(() -> new ImportProfile(businessId, signature));
        p.setMapping(json.writeValueAsString(mapping), yerevan.now().toInstant());
        profiles.saveAndFlush(p);
        List<ImportProfile> all = profiles.findByBusinessIdOrderByUpdatedAtAsc(businessId);
        if (all.size() > MAX_IMPORT_PROFILES) profiles.deleteAll(all.subList(0, all.size() - MAX_IMPORT_PROFILES));
    }

    /**
     * Ключи — как в приложении: date, amount, debit, desc, currencyCol — номера колонок (-1 — нет);
     * dateFormat и currency — строки.
     */
    static Map<String, Object> parseImportProfile(Map<String, Object> json) {
        Fields f = new Fields(json);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String key : List.of("date", "amount", "debit", "desc", "currencyCol")) {
            m.put(key, f.intIn(key, -1, 200, "Неверная колонка «" + key + "»"));
        }
        String dateFormat = f.str("dateFormat", 20, true);
        if (!List.of("dd.mm.yyyy", "yyyy-mm-dd", "mm/dd/yyyy").contains(dateFormat)) {
            throw ApiException.badRequest("Неизвестный формат даты: " + dateFormat);
        }
        m.put("dateFormat", dateFormat);
        m.put("currency", f.enumValue("currency", Currency.class).name());
        return m;
    }
}
