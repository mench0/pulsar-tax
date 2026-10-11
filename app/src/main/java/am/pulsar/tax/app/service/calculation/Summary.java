package am.pulsar.tax.app.service.calculation;import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.service.calculation.TaxRules.ThresholdStatus;
import am.pulsar.tax.dto.enums.TaxRegime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Годовая сводка: оборот, налог и уплата по кварталам, порог режима, предупреждения и сроки.
 * Предупреждения и сроки отдаются кодами с параметрами: текст на нужном языке собирает фронтенд.
 */
public final class Summary {

    private Summary() {
    }

    /** Сводка с расходами: они показываются рядом с доходом, но на налог пока не влияют. */
    public static Map<String, Object> build(Business profile, List<Income> yearIncomes, List<TaxPayment> yearPayments,
                                     List<Expense> yearExpenses, int year, LocalDate today) {
        BigDecimal[] byQuarter = zeros(4);
        for (Income i : yearIncomes) {
            int q = (i.getDate().getMonthValue() - 1) / 3;
            byQuarter[q] = byQuarter[q].add(i.getAmount());
        }
        BigDecimal[] spent = zeros(4);
        for (Expense e : yearExpenses) {
            int q = (e.getDate().getMonthValue() - 1) / 3;
            spent[q] = spent[q].add(e.getAmount());
        }
        // Индекс 0 — уплаты за год целиком (микробизнес), 1–4 — за кварталы.
        BigDecimal[] paid = zeros(5);
        for (TaxPayment p : yearPayments) {
            paid[p.getQuarter()] = paid[p.getQuarter()].add(p.getAmount());
        }

        BigDecimal rate = TaxRules.ratePercent(profile, year);
        boolean turnover = profile.getRegime() == TaxRegime.TURNOVER;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalTax = rate == null ? null : BigDecimal.ZERO;
        BigDecimal totalPaid = paid[0];
        BigDecimal totalDue = BigDecimal.ZERO;
        List<Map<String, Object>> quarters = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            // Налог квартала — из налогового движка: те же правила по датам операций, что и в подробном расчёте.
            BigDecimal tax = rate == null ? null : TaxEngine.calculate(profile, year, q + 1, yearIncomes, List.of(), List.of(), null)
                    .totals().accrued();
            BigDecimal due = tax == null ? null : tax.subtract(paid[q + 1]).max(BigDecimal.ZERO);
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("quarter", q + 1);
            qm.put("income", byQuarter[q]);
            qm.put("tax", tax);
            qm.put("paid", paid[q + 1]);
            qm.put("due", due);
            qm.put("expenses", spent[q]);
            qm.put("dueDate", turnover ? quarterDue(year, q + 1) : null);
            quarters.add(qm);
            total = total.add(byQuarter[q]);
            totalPaid = totalPaid.add(paid[q + 1]);
            if (totalTax != null) totalTax = tax == null ? null : totalTax.add(tax);
            if (due != null) totalDue = totalDue.add(due);
        }

        TaxRules.YearRules yearRules = TaxRules.forYear(year);
        BigDecimal limit = yearRules == null ? null : yearRules.threshold(profile.getRegime());
        ThresholdStatus status = TaxRules.status(total, limit);
        boolean regimeAllowed = TaxRules.regimeAllowed(profile, year);

        List<Map<String, Object>> warnings = new ArrayList<>();
        if (!regimeAllowed) {
            warnings.add(notice("REGIME_NOT_ALLOWED", Map.of(
                    "activity", profile.getActivity(), "regime", profile.getRegime(), "year", year)));
        }
        if (profile.getRegime() == TaxRegime.GENERAL) warnings.add(notice("GENERAL_NOT_SUPPORTED", Map.of()));
        if (yearRules == null) warnings.add(notice("RULES_MISSING_FOR_PERIOD", Map.of("firstKnownDate", TaxRules.firstKnownDate())));
        if (turnover && rate == null && regimeAllowed && yearRules != null) warnings.add(notice("RATE_MISSING", Map.of()));
        if (status == ThresholdStatus.WARNING) warnings.add(notice("THRESHOLD_WARNING", Map.of()));
        if (status == ThresholdStatus.EXCEEDED) warnings.add(notice("THRESHOLD_EXCEEDED", Map.of()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", year);
        m.put("regime", profile.getRegime());
        m.put("activity", profile.getActivity());
        m.put("regimeAllowed", regimeAllowed);
        m.put("ratePercent", rate);
        m.put("totalIncome", total);
        m.put("totalTax", totalTax);
        m.put("totalPaid", totalPaid);
        m.put("totalDue", totalTax == null ? null : totalDue);
        m.put("totalExpenses", Arrays.stream(spent).reduce(BigDecimal.ZERO, BigDecimal::add));
        // «Копилка»: какую долю каждого поступления откладывать на налог — это и есть ставка.
        m.put("setAsidePercent", rate);
        m.put("threshold", limit);
        m.put("remaining", limit == null ? null : limit.subtract(total).max(BigDecimal.ZERO));
        m.put("thresholdPercent", TaxRules.percentOf(total, limit));
        m.put("thresholdStatus", status);
        m.put("quarters", quarters);
        m.put("warnings", warnings);
        m.put("deadlines", deadlines(profile, year, today));
        return m;
    }

    /** Налог с оборота за квартал платится до 20-го числа месяца, следующего за кварталом. */
    public static LocalDate quarterDue(int year, int quarter) {
        return LocalDate.of(year, quarter * 3, 1).plusMonths(1).withDayOfMonth(20);
    }

    /** Сроки года, которые ещё не прошли. Срок без даты (date = null) нужно уточнить. */
    public static List<Map<String, Object>> deadlines(Business profile, int year, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (profile.getRegime() == TaxRegime.TURNOVER) {
            for (int q = 1; q <= 4; q++) {
                addDeadline(list, today, quarterDue(year, q), "TURNOVER_QUARTER", Map.of("quarter", q, "year", year));
            }
        }
        addDeadline(list, today, LocalDate.of(year + 1, 2, 20), "REGIME_CHOICE", Map.of("year", year + 1));
        if (profile.getRegime() == TaxRegime.MICRO) {
            Map<String, Object> d = notice("MICRO_ANNUAL_REPORT", Map.of("year", year));
            d.put("date", null);
            list.add(d);
        }
        return list;
    }

    private static void addDeadline(List<Map<String, Object>> list, LocalDate today, LocalDate date,
                                    String code, Map<String, Object> params) {
        if (date.isBefore(today)) return;
        Map<String, Object> d = notice(code, params);
        d.put("date", date);
        list.add(d);
    }

    private static Map<String, Object> notice(String code, Map<String, Object> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("params", params);
        return m;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }
}
