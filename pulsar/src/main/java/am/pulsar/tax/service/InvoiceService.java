package am.pulsar.tax.service;

import am.pulsar.tax.entity.Enums.Currency;
import am.pulsar.tax.entity.Enums.InvoiceStatus;
import am.pulsar.tax.entity.Income;
import am.pulsar.tax.entity.Invoice;
import am.pulsar.tax.entity.InvoiceItem;
import am.pulsar.tax.exception.ApiException;
import am.pulsar.tax.repository.IncomeRepository;
import am.pulsar.tax.repository.InvoiceRepository;
import am.pulsar.tax.util.Fields;
import am.pulsar.tax.util.Yerevan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final IncomeRepository incomes;
    private final Yerevan yerevan;

    public InvoiceService(InvoiceRepository invoices, IncomeRepository incomes, Yerevan yerevan) {
        this.invoices = invoices;
        this.incomes = incomes;
        this.yerevan = yerevan;
    }

    @Transactional(readOnly = true)
    public List<Invoice> list(long businessId) {
        return invoices.findByBusinessIdOrderByDateDescIdDesc(businessId);
    }

    @Transactional(readOnly = true)
    public Invoice get(long businessId, long id) {
        return invoices.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Счёт не найден"));
    }

    /** Добавляет счёт; без номера присваивает следующий вида PT-2026-001. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Invoice add(long businessId, Map<String, Object> json) {
        Fields f = new Fields(json);
        String number = f.str("number", 50, false);
        LocalDate date = f.date("date", false);
        if (date == null) date = yerevan.today();
        LocalDate dueDate = f.date("dueDate", false);
        String client = f.str("client", 200, true);
        String clientTin = f.str("clientTin", 20, false);
        String eInvoice = f.str("eInvoiceNumber", 50, false);
        List<Object> raw = f.list("items");
        if (raw.isEmpty()) throw ApiException.badRequest("Добавьте хотя бы одну позицию в счёт");
        if (raw.size() > 200) throw ApiException.badRequest("В счёте не больше 200 позиций");
        List<InvoiceItem> items = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map)) throw ApiException.badRequest("Неверная позиция счёта");
            Fields it = new Fields((Map<String, Object>) o);
            BigDecimal qty = it.dec("quantity", true);
            BigDecimal price = it.dec("price", true);
            if (qty.signum() <= 0) throw ApiException.badRequest("Количество должно быть больше нуля");
            if (price.signum() < 0) throw ApiException.badRequest("Цена не может быть отрицательной");
            items.add(new InvoiceItem(it.str("name", 300, true), qty, price));
        }
        if (number.isEmpty()) {
            number = nextNumber(businessId, date.getYear());
        } else if (invoices.existsByBusinessIdAndNumberIgnoreCase(businessId, number)) {
            throw new ApiException(409, "Счёт с номером " + number + " уже существует");
        }
        return invoices.save(new Invoice(businessId, number, date, dueDate, client, clientTin, eInvoice, items));
    }

    private String nextNumber(long businessId, int year) {
        String prefix = "PT-" + year + "-";
        int max = invoices.numbersStartingWith(businessId, prefix).stream()
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

    @Transactional
    public Invoice setEInvoiceNumber(long businessId, long id, Map<String, Object> body) {
        Invoice inv = get(businessId, id);
        inv.setEInvoiceNumber(new Fields(body).str("eInvoiceNumber", 50, false));
        return inv;
    }

    /** Отмечает счёт оплаченным и записывает доход на сумму счёта. Повторный вызов ничего не меняет. */
    @Transactional
    public Invoice markPaid(long businessId, long id, LocalDate paidOn) {
        Invoice inv = get(businessId, id);
        if (inv.getStatus() == InvoiceStatus.PAID) return inv;
        BigDecimal total = inv.total();
        if (total.signum() <= 0) throw ApiException.badRequest("Сумма счёта равна нулю — доход не записать");
        inv.setStatus(InvoiceStatus.PAID);
        incomes.save(new Income(businessId, paidOn, total, Currency.AMD, null, null, inv.getClient(),
                "Оплата по счёту " + inv.getNumber(), inv.getId(), null));
        return inv;
    }

    /** Удаляет счёт; доход, записанный при оплате, база удаляет вместе с ним. */
    @Transactional
    public void delete(long businessId, long id) {
        invoices.delete(get(businessId, id));
    }
}
