package am.pulsar.tax;

import am.pulsar.tax.Model.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

/**
 * Данные одного ИП в одном JSON-файле. Данные держатся в памяти,
 * каждое изменение атомарно записывается на диск. Если запись не удалась,
 * состояние в памяти откатывается к последнему сохранённому.
 */
public final class Store {

    private final Path file;
    private Profile profile = new Profile();
    private final List<Income> incomes = new ArrayList<>();
    private final List<Invoice> invoices = new ArrayList<>();
    private final List<TaxPayment> payments = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();
    /** Сопоставление колонок выписки по отпечатку заголовка: следующая выписка того же банка разберётся сама. */
    private final Map<String, Map<String, Object>> importProfiles = new LinkedHashMap<>();
    private long nextId = 1;

    /** file == null — хранить только в памяти (для тестов). */
    public Store(Path file) {
        this.file = file;
        if (file != null && Files.exists(file)) load();
    }

    // ---------- Профиль ----------

    public synchronized Profile profile() {
        return profile;
    }

    public synchronized Profile updateProfile(Profile p) {
        profile = p;
        save();
        return p;
    }

    // ---------- Доходы ----------

    public synchronized List<Income> incomes(int year) {
        return incomes.stream()
                .filter(i -> i.date.getYear() == year)
                .sorted(Comparator.comparing((Income i) -> i.date).thenComparing(i -> i.id).reversed())
                .toList();
    }

    public synchronized List<Income> allIncomes() {
        return incomes.stream()
                .sorted(Comparator.comparing((Income i) -> i.date).thenComparing(i -> i.id))
                .toList();
    }

    public synchronized Income addIncome(Income i) {
        i.id = nextId++;
        incomes.add(i);
        save();
        return i;
    }

    /**
     * Добавляет доходы из выписки одной записью на диск. Строки с уже известным importKey
     * пропускаются, поэтому одну и ту же выписку можно загрузить повторно.
     *
     * @return {added, skipped}
     */
    public synchronized int[] importIncomes(List<Income> list) {
        Set<String> known = new HashSet<>();
        for (Income i : incomes) {
            if (i.importKey != null) known.add(i.importKey);
        }
        int added = 0;
        int skipped = 0;
        for (Income i : list) {
            if (i.importKey != null && !known.add(i.importKey)) {
                skipped++;
                continue;
            }
            i.id = nextId++;
            incomes.add(i);
            added++;
        }
        if (added > 0) save();
        return new int[]{added, skipped};
    }

    /** Удаляет доход; если он записан при оплате счёта, счёт снова становится неоплаченным. */
    public synchronized void deleteIncome(long id) {
        Income income = incomes.stream().filter(i -> i.id == id).findFirst()
                .orElseThrow(() -> new ApiException(404, "Запись о доходе не найдена"));
        incomes.remove(income);
        // Без дохода счёт снова считается неоплаченным, иначе оплату нельзя отметить заново.
        if (income.invoiceId != null) {
            invoices.stream().filter(inv -> inv.id == income.invoiceId).findFirst()
                    .ifPresent(inv -> inv.status = InvoiceStatus.SENT);
        }
        save();
    }

    // ---------- Счета ----------

    public synchronized List<Invoice> invoices() {
        return invoices.stream()
                .sorted(Comparator.comparing((Invoice i) -> i.date).thenComparing(i -> i.id).reversed())
                .toList();
    }

    public synchronized Invoice invoice(long id) {
        return invoices.stream().filter(i -> i.id == id).findFirst()
                .orElseThrow(() -> new ApiException(404, "Счёт не найден"));
    }

    /** Добавляет счёт; без номера присваивает следующий вида PT-2026-001. */
    public synchronized Invoice addInvoice(Invoice inv) {
        if (inv.number == null || inv.number.isBlank()) {
            inv.number = nextNumber(inv.date.getYear());
        } else if (invoices.stream().anyMatch(i -> i.number.equalsIgnoreCase(inv.number))) {
            throw new ApiException(409, "Счёт с номером " + inv.number + " уже существует");
        }
        inv.id = nextId++;
        invoices.add(inv);
        save();
        return inv;
    }

    /** Записывает номер официального электронного счёта из e-invoice.taxservice.am. */
    public synchronized Invoice setEInvoiceNumber(long id, String number) {
        Invoice inv = invoice(id);
        inv.eInvoiceNumber = number;
        save();
        return inv;
    }

    /** Отмечает счёт оплаченным и записывает доход на сумму счёта. Повторный вызов ничего не меняет. */
    public synchronized Invoice markPaid(long id, LocalDate paidOn) {
        Invoice inv = invoice(id);
        if (inv.status == InvoiceStatus.PAID) return inv;
        inv.status = InvoiceStatus.PAID;
        Income income = new Income();
        income.id = nextId++;
        income.date = paidOn;
        income.amount = inv.total();
        income.client = inv.client;
        income.description = "Оплата по счёту " + inv.number;
        income.invoiceId = inv.id;
        incomes.add(income);
        save();
        return inv;
    }

    /** Удаляет счёт и доход, записанный при его оплате. */
    public synchronized void deleteInvoice(long id) {
        if (!invoices.removeIf(i -> i.id == id)) throw new ApiException(404, "Счёт не найден");
        // Доход, записанный при оплате, удаляется вместе со счётом, чтобы не ссылаться на пустоту.
        incomes.removeIf(i -> i.invoiceId != null && i.invoiceId == id);
        save();
    }

    private String nextNumber(int year) {
        String prefix = "PT-" + year + "-";
        int max = invoices.stream()
                .map(i -> i.number)
                .filter(n -> n.startsWith(prefix))
                .mapToInt(n -> {
                    try {
                        return Integer.parseInt(n.substring(prefix.length()));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max().orElse(0);
        return prefix + String.format("%03d", max + 1);
    }

    // ---------- Уплата налога ----------

    public synchronized List<TaxPayment> payments(int year) {
        return payments.stream()
                .filter(p -> p.year == year)
                .sorted(Comparator.comparing((TaxPayment p) -> p.date).thenComparing(p -> p.id))
                .toList();
    }

    public synchronized TaxPayment addPayment(TaxPayment p) {
        p.id = nextId++;
        payments.add(p);
        save();
        return p;
    }

    public synchronized void deletePayment(long id) {
        if (!payments.removeIf(p -> p.id == id)) throw new ApiException(404, "Запись об уплате не найдена");
        save();
    }

    // ---------- Расходы ----------

    public synchronized List<Expense> expenses(int year) {
        return expenses.stream()
                .filter(e -> e.date.getYear() == year)
                .sorted(Comparator.comparing((Expense e) -> e.date).thenComparing(e -> e.id).reversed())
                .toList();
    }

    public synchronized List<Expense> allExpenses() {
        return expenses.stream()
                .sorted(Comparator.comparing((Expense e) -> e.date).thenComparing(e -> e.id))
                .toList();
    }

    public synchronized Expense addExpense(Expense e) {
        e.id = nextId++;
        expenses.add(e);
        save();
        return e;
    }

    public synchronized void deleteExpense(long id) {
        if (!expenses.removeIf(e -> e.id == id)) throw new ApiException(404, "Запись о расходе не найдена");
        save();
    }

    // ---------- Настройки импорта ----------

    public synchronized Map<String, Map<String, Object>> importProfiles() {
        return new LinkedHashMap<>(importProfiles);
    }

    public synchronized void saveImportProfile(String signature, Map<String, Object> mapping) {
        importProfiles.remove(signature);
        importProfiles.put(signature, mapping);
        // Хватит с запасом на все банки пользователя; самые старые настройки уходят первыми.
        while (importProfiles.size() > 20) importProfiles.remove(importProfiles.keySet().iterator().next());
        save();
    }

    // ---------- Экспорт ----------

    /** Все данные в том же виде, что и в файле. */
    public synchronized Map<String, Object> toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("nextId", nextId);
        root.put("profile", profile.toJson());
        root.put("incomes", incomes.stream().map(Income::toJson).toList());
        root.put("invoices", invoices.stream().map(Invoice::toJson).toList());
        root.put("payments", payments.stream().map(TaxPayment::toJson).toList());
        root.put("expenses", expenses.stream().map(Expense::toJson).toList());
        root.put("importProfiles", new LinkedHashMap<>(importProfiles));
        return root;
    }

    // ---------- Файл ----------

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "pulsar", ".tmp");
            Files.writeString(tmp, Json.write(toJson()), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            rollback();
            throw new ApiException(500, "Не удалось сохранить данные: " + e.getMessage());
        }
    }

    /** Возвращает память к содержимому файла, чтобы она не расходилась с диском. */
    private void rollback() {
        profile = new Profile();
        incomes.clear();
        invoices.clear();
        payments.clear();
        expenses.clear();
        importProfiles.clear();
        nextId = 1;
        if (Files.exists(file)) load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        Map<String, Object> root;
        try {
            root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Не удалось прочитать " + file + ": " + e.getMessage(), e);
        }
        try {
            nextId = longValue(root.get("nextId"), 1);
            if (root.get("profile") instanceof Map<?, ?> p) profile = Codec.profile((Map<String, Object>) p);
            for (Map<String, Object> m : objects(root.get("incomes"))) {
                // Файлы до округления до драма могли содержать суммы с 4 знаками.
                Map<String, Object> c = new HashMap<>(m);
                if (c.get("amount") instanceof BigDecimal a && a.scale() > 2) c.put("amount", a.setScale(2, RoundingMode.HALF_UP));
                Income i = Codec.income(c);
                i.amount = (BigDecimal) c.get("amount");
                i.id = longValue(m.get("id"), 0);
                Object inv = m.get("invoiceId");
                i.invoiceId = inv == null ? null : longValue(inv, 0);
                incomes.add(i);
            }
            for (Map<String, Object> m : objects(root.get("invoices"))) {
                Invoice inv = Codec.invoice(m);
                inv.id = longValue(m.get("id"), 0);
                inv.number = (String) m.get("number");
                inv.status = InvoiceStatus.valueOf((String) m.get("status"));
                invoices.add(inv);
            }
            for (Map<String, Object> m : objects(root.get("expenses"))) {
                Expense e = Codec.expense(m);
                e.id = longValue(m.get("id"), 0);
                expenses.add(e);
            }
            if (root.get("importProfiles") instanceof Map<?, ?> profiles) {
                for (Map.Entry<?, ?> e : profiles.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> m) {
                        importProfiles.put((String) e.getKey(), Codec.importProfile((Map<String, Object>) m));
                    }
                }
            }
            for (Map<String, Object> m : objects(root.get("payments"))) {
                TaxPayment p = Codec.taxPayment(m);
                p.id = longValue(m.get("id"), 0);
                payments.add(p);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Файл " + file + " повреждён: " + e.getMessage()
                    + ". Восстановите его из папки backups.", e);
        }
        long maxId = 0;
        for (Income i : incomes) maxId = Math.max(maxId, i.id);
        for (Invoice i : invoices) maxId = Math.max(maxId, i.id);
        for (TaxPayment p : payments) maxId = Math.max(maxId, p.id);
        for (Expense e : expenses) maxId = Math.max(maxId, e.id);
        nextId = Math.max(nextId, maxId + 1);
    }

    private static long longValue(Object v, long def) {
        return v instanceof BigDecimal d ? d.longValueExact() : def;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objects(Object v) {
        if (!(v instanceof List<?> l)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }
}
