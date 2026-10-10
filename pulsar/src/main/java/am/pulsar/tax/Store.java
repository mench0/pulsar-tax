package am.pulsar.tax;

import am.pulsar.tax.Model.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

/**
 * Хранилище в одном JSON-файле. Данные держатся в памяти,
 * каждое изменение атомарно записывается на диск.
 * Подходит для одного ИП; при росте заменяется на базу данных.
 */
public final class Store {

    private final Path file;
    private Profile profile = new Profile();
    private final List<Income> incomes = new ArrayList<>();
    private final List<Invoice> invoices = new ArrayList<>();
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

    public synchronized Income addIncome(Income i) {
        i.id = nextId++;
        incomes.add(i);
        save();
        return i;
    }

    public synchronized void deleteIncome(long id) {
        if (!incomes.removeIf(i -> i.id == id)) throw new ApiException(404, "Запись о доходе не найдена");
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

    public synchronized void deleteInvoice(long id) {
        if (!invoices.removeIf(i -> i.id == id)) throw new ApiException(404, "Счёт не найден");
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

    // ---------- Файл ----------

    private void save() {
        if (file == null) return;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("nextId", nextId);
        root.put("profile", profile.toJson());
        root.put("incomes", incomes.stream().map(Income::toJson).toList());
        root.put("invoices", invoices.stream().map(Invoice::toJson).toList());
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "pulsar", ".tmp");
            Files.writeString(tmp, Json.write(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ApiException(500, "Не удалось сохранить данные: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            nextId = ((BigDecimal) root.get("nextId")).longValueExact();
            profile = Codec.profile((Map<String, Object>) root.get("profile"));
            for (Object o : (List<Object>) root.get("incomes")) {
                Map<String, Object> m = (Map<String, Object>) o;
                Income i = Codec.income(m);
                i.id = ((BigDecimal) m.get("id")).longValueExact();
                Object inv = m.get("invoiceId");
                i.invoiceId = inv == null ? null : ((BigDecimal) inv).longValueExact();
                incomes.add(i);
            }
            for (Object o : (List<Object>) root.get("invoices")) {
                Map<String, Object> m = (Map<String, Object>) o;
                Invoice inv = Codec.invoice(m);
                inv.id = ((BigDecimal) m.get("id")).longValueExact();
                inv.number = (String) m.get("number");
                inv.status = InvoiceStatus.valueOf((String) m.get("status"));
                invoices.add(inv);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + file + ": " + e.getMessage(), e);
        }
    }
}
