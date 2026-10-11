package am.pulsar.tax.app.service;

import am.pulsar.tax.app.entity.BankTransaction;
import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.repository.BankTransactionRepository;
import am.pulsar.tax.app.repository.BusinessRepository;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.service.calculation.Planning;
import am.pulsar.tax.app.service.calculation.Summary;
import am.pulsar.tax.app.service.calculation.TaxEngine.Calculation;
import am.pulsar.tax.app.service.calculation.TaxRules;
import am.pulsar.tax.dto.enums.TaxRegime;
import am.pulsar.tax.dto.enums.TransactionKind;
import am.pulsar.tax.dto.enums.TransactionStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Налоговый резерв и прогноз денег. Начисленные и уплаченные суммы — из налогового движка (факт),
 * налог с будущих поступлений и прогноз потоков — оценки с явными допущениями.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlanningService {

    /** Сколько полных месяцев истории брать для прогноза. */
    static final int HISTORY_MONTHS = 6;

    TaxCalculationService calculations;
    ReconciliationService reconciliation;
    BankTransactionRepository transactions;
    IncomeRepository incomes;
    ExpenseRepository expenses;
    BusinessRepository businesses;

    /** Обязательство за период: начислено (факт), уплачено, осталось, срок. */
    public record Obligation(int year, int quarter, BigDecimal accrued, BigDecimal paid, BigDecimal due,
                             LocalDate deadline, String state, boolean complete) {
    }

    public record Reserve(LocalDate asOf, TaxRegime regime, List<Obligation> obligations,
                          BigDecimal accruedUnpaid, BigDecimal receivables, BigDecimal ratePercent,
                          BigDecimal expectedOnReceivables, BigDecimal recommended,
                          BigDecimal actual, LocalDate actualAsOf, BigDecimal gap,
                          LocalDate nextDeadline, BigDecimal nextDeadlineAmount,
                          List<Map<String, Object>> steps, List<String> notes) {
    }

    @Transactional(readOnly = true)
    public Reserve reserve(Business b, LocalDate today) {
        List<Obligation> obligations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        BigDecimal unpaid = BigDecimal.ZERO;
        if (b.getRegime() == TaxRegime.TURNOVER) {
            for (int year = today.getYear() - 1; year <= today.getYear(); year++) {
                for (int q = 1; q <= 4; q++) {
                    LocalDate start = LocalDate.of(year, q * 3 - 2, 1);
                    if (start.isAfter(today)) break;
                    Calculation c = calculations.calculate(b, year, q);
                    LocalDate deadline = Summary.quarterDue(year, q);
                    boolean current = !c.periodTo().isBefore(today);
                    BigDecimal due = c.totals().due();
                    if (due == null) {
                        // Не посчитать (нет правил за период или ошибки данных) — показываем как неполное.
                        if (c.totals().turnover().signum() > 0) {
                            obligations.add(new Obligation(year, q, null, c.totals().paid(), null, deadline, "INCOMPLETE", false));
                        }
                        continue;
                    }
                    if (due.signum() == 0 && !current) continue;
                    String state = current ? "IN_PROGRESS" : deadline.isBefore(today) ? "OVERDUE" : "UPCOMING";
                    obligations.add(new Obligation(year, q, c.totals().accrued(), c.totals().paid(), due, deadline, state, true));
                    unpaid = unpaid.add(due);
                }
            }
            if (obligations.stream().anyMatch(o -> !o.complete())) notes.add("INCOMPLETE_PERIODS");
        } else if (b.getRegime() == TaxRegime.MICRO) {
            notes.add("MICRO_NO_TURNOVER_TAX");
        } else {
            notes.add("GENERAL_NOT_SUPPORTED");
        }
        notes.add("EMPLOYEE_TAXES_NOT_INCLUDED");

        var overview = reconciliation.overview(b.getId(), today);
        BigDecimal receivables = overview.receivable();
        BigDecimal rate = b.getRegime() == TaxRegime.TURNOVER ? TaxRules.ratePercent(b, today) : null;
        BigDecimal expected = rate == null ? BigDecimal.ZERO
                : receivables.multiply(rate).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        if (rate != null && receivables.signum() > 0) notes.add("RECEIVABLES_ESTIMATE");
        BigDecimal recommended = unpaid.add(expected);
        BigDecimal gap = b.getReserveBalance() == null ? null : recommended.subtract(b.getReserveBalance());

        Obligation next = obligations.stream().filter(o -> o.due() != null && o.due().signum() > 0 && !o.deadline().isBefore(today))
                .min((x, y) -> x.deadline().compareTo(y.deadline())).orElse(null);
        // Текущий квартал платится в следующем сроке: ближайший срок — по закончившимся кварталам или текущему.
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("ACCRUED_UNPAID", unpaid, Map.of("periods", obligations.size())));
        if (expected.signum() > 0) steps.add(step("RECEIVABLES_TAX_ESTIMATE", expected, Map.of("receivables", receivables, "rate", rate)));
        steps.add(step("RECOMMENDED", recommended, Map.of()));
        if (b.getReserveBalance() != null) {
            steps.add(step("ACTUAL_RESERVE", b.getReserveBalance(), Map.of()));
            steps.add(step(gap.signum() > 0 ? "SHORTFALL" : "SURPLUS", gap.abs(), Map.of()));
        } else {
            notes.add("ACTUAL_RESERVE_UNKNOWN");
        }
        return new Reserve(today, b.getRegime(), obligations, unpaid, receivables, rate, expected, recommended,
                b.getReserveBalance(), b.getBalancesAsOf(), gap,
                next == null ? null : next.deadline(), next == null ? null : next.due(), steps, notes);
    }

    @Transactional(readOnly = true)
    public Planning.Forecast forecast(Business b, int months, LocalDate today) {
        if (months < 1 || months > 12) throw new CustomValidationException("Прогноз — от 1 до 12 месяцев");
        YearMonth to = YearMonth.from(today).minusMonths(1);
        YearMonth from = to.minusMonths(HISTORY_MONTHS - 1);
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        Map<YearMonth, BigDecimal[]> flows = new TreeMap<>();
        List<BankTransaction> bank = transactions.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(b.getId(), fromDate, toDate)
                .stream().filter(t -> t.getStatus() == TransactionStatus.CONFIRMED && t.getAmountAmd() != null).toList();
        if (!bank.isEmpty()) {
            for (BankTransaction t : bank) {
                if (t.getKind() == TransactionKind.OWN_TRANSFER || t.getKind() == TransactionKind.TAX_PAYMENT) continue;
                BigDecimal[] f = flows.computeIfAbsent(YearMonth.from(t.getDate()), k -> zero());
                if (t.getKind() == TransactionKind.INCOME) f[0] = f[0].add(t.getAmountAmd().abs());
                else if (!t.incoming()) f[1] = f[1].add(t.getAmountAmd().abs());
            }
        } else {
            for (Income i : incomes.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(b.getId(), fromDate, toDate)) {
                flows.computeIfAbsent(YearMonth.from(i.getDate()), k -> zero())[0] =
                        flows.get(YearMonth.from(i.getDate()))[0].add(i.getAmount());
            }
            for (Expense e : expenses.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(b.getId(), fromDate, toDate)) {
                flows.computeIfAbsent(YearMonth.from(e.getDate()), k -> zero())[1] =
                        flows.get(YearMonth.from(e.getDate()))[1].add(e.getAmount());
            }
        }
        // Месяцы без операций после первого известного — нули: это тоже история (месяц без дохода).
        List<Planning.MonthFlow> history = new ArrayList<>();
        if (!flows.isEmpty()) {
            for (YearMonth m = flows.keySet().iterator().next(); !m.isAfter(to); m = m.plusMonths(1)) {
                BigDecimal[] f = flows.getOrDefault(m, zero());
                history.add(new Planning.MonthFlow(m, f[0], f[1]));
            }
        }

        List<Planning.Receivable> receivables = new ArrayList<>();
        for (var inv : reconciliation.overview(b.getId(), today).invoices()) {
            if (inv.balance().signum() <= 0) continue;
            LocalDate expected = inv.dueDate() != null ? inv.dueDate() : inv.date().plusDays(30);
            receivables.add(new Planning.Receivable(inv.id(), inv.number(), inv.balance(), expected, expected.isBefore(today)));
        }

        List<Planning.TaxDue> known = new ArrayList<>();
        for (Obligation o : reserve(b, today).obligations()) {
            if (o.due() != null && o.due().signum() > 0) {
                known.add(new Planning.TaxDue(o.year() + "-Q" + o.quarter(), o.deadline(), o.due(), true));
            }
        }
        BigDecimal rate = b.getRegime() == TaxRegime.TURNOVER ? TaxRules.ratePercent(b, today) : null;
        return Planning.forecast(today, months, history, receivables, known, rate, b.getCashBalance(), b.getBalancesAsOf(),
                Summary::quarterDue);
    }

    @Transactional
    public Business updateBalances(Business b, BigDecimal reserve, BigDecimal cash, LocalDate asOf, LocalDate today) {
        if (reserve != null && reserve.signum() < 0) throw new CustomValidationException("Резерв не может быть отрицательным");
        if (asOf != null && asOf.isAfter(today)) throw new CustomValidationException("Дата остатков не может быть в будущем");
        b.setReserveBalance(reserve);
        b.setCashBalance(cash);
        b.setBalancesAsOf(asOf == null && (reserve != null || cash != null) ? today : asOf);
        return businesses.save(b);
    }

    private static BigDecimal[] zero() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static Map<String, Object> step(String code, BigDecimal amount, Map<String, Object> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("amount", amount);
        m.put("params", params);
        return m;
    }
}
