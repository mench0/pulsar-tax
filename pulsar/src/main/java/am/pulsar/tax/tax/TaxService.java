package am.pulsar.tax.tax;

import am.pulsar.tax.accounting.AccountingService;
import am.pulsar.tax.business.Business;
import am.pulsar.tax.shared.ApiException;
import am.pulsar.tax.shared.Fields;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Сводка за год и учёт уплаченного налога. */
@Service
public class TaxService {

    private final TaxPaymentRepository payments;
    private final AccountingService accounting;

    public TaxService(TaxPaymentRepository payments, AccountingService accounting) {
        this.payments = payments;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(Business b, int year, LocalDate today) {
        return Summary.build(b, accounting.incomes(b.getId(), year), payments(b.getId(), year),
                accounting.expenses(b.getId(), year), year, today);
    }

    @Transactional(readOnly = true)
    public List<TaxPayment> payments(long businessId, int year) {
        return payments.findByBusinessIdAndYearOrderByDateAscIdAsc(businessId, year);
    }

    @Transactional(readOnly = true)
    public List<TaxPayment> allPayments(long businessId) {
        return payments.findByBusinessIdOrderByDateAscIdAsc(businessId);
    }

    @Transactional
    public TaxPayment addPayment(long businessId, Map<String, Object> json) {
        Fields f = new Fields(json);
        int year = f.intIn("year", 2000, 2100, "Неверный год");
        int quarter = f.intIn("quarter", 0, 4, "Квартал должен быть от 1 до 4 (0 — за год)");
        LocalDate date = f.date("date", true);
        BigDecimal amount = f.dec("amount", true);
        if (amount.signum() <= 0) throw ApiException.badRequest("Сумма уплаты должна быть больше нуля");
        if (amount.scale() > 2) throw ApiException.badRequest("Сумма уплаты: не больше 2 знаков после запятой");
        return payments.save(new TaxPayment(businessId, year, quarter, date, amount));
    }

    @Transactional
    public void deletePayment(long businessId, long id) {
        payments.delete(payments.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Запись об уплате не найдена")));
    }
}
