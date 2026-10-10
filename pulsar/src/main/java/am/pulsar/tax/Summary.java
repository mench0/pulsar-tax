package am.pulsar.tax;

import am.pulsar.tax.Model.Income;
import am.pulsar.tax.Model.Profile;
import am.pulsar.tax.Model.TaxRegime;
import am.pulsar.tax.TaxRules.ThresholdStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Годовая сводка: оборот, налог по кварталам, порог режима, предупреждения и ближайшие сроки. */
final class Summary {

    private Summary() {
    }

    static Map<String, Object> build(Profile profile, List<Income> yearIncomes, int year, LocalDate today) {
        BigDecimal[] byQuarter = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Income i : yearIncomes) {
            int q = (i.date.getMonthValue() - 1) / 3;
            byQuarter[q] = byQuarter[q].add(i.amount);
        }

        BigDecimal rate = TaxRules.ratePercent(profile);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalTax = rate == null ? null : BigDecimal.ZERO;
        List<Map<String, Object>> quarters = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            BigDecimal tax = TaxRules.tax(byQuarter[q], rate);
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("quarter", q + 1);
            qm.put("income", byQuarter[q]);
            qm.put("tax", tax);
            qm.put("dueDate", profile.regime == TaxRegime.TURNOVER ? quarterDue(year, q + 1) : null);
            quarters.add(qm);
            total = total.add(byQuarter[q]);
            if (totalTax != null) totalTax = totalTax.add(tax);
        }

        BigDecimal limit = TaxRules.threshold(profile.regime);
        ThresholdStatus status = TaxRules.status(total, limit);

        List<String> warnings = new ArrayList<>();
        if (profile.regime == TaxRegime.GENERAL) {
            warnings.add("Общая система налогообложения пока не рассчитывается. Для неё нужен бухгалтер.");
        }
        if (profile.regime == TaxRegime.TURNOVER && rate == null) {
            warnings.add("Укажите ставку налога с оборота в настройках.");
        }
        if (status == ThresholdStatus.WARNING) {
            warnings.add("Оборот приближается к порогу режима. Заранее решите, какой режим выбрать на следующий год.");
        }
        if (status == ThresholdStatus.EXCEEDED) {
            warnings.add("Оборот превысил порог режима. Уточните у бухгалтера или в налоговой, как перейти на другой режим.");
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", year);
        m.put("regime", profile.regime);
        m.put("activity", profile.activity);
        m.put("ratePercent", rate);
        m.put("totalIncome", total);
        m.put("totalTax", totalTax);
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
    static LocalDate quarterDue(int year, int quarter) {
        return LocalDate.of(year, quarter * 3, 1).plusMonths(1).withDayOfMonth(20);
    }

    private static List<Map<String, Object>> deadlines(Profile profile, int year, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (profile.regime == TaxRegime.TURNOVER) {
            for (int q = 1; q <= 4; q++) {
                addDeadline(list, today, quarterDue(year, q), "Расчёт и уплата налога с оборота за " + q + " квартал");
            }
        }
        addDeadline(list, today, LocalDate.of(year + 1, 2, 20),
                "Заявление о выборе налогового режима на " + (year + 1) + " год");
        if (profile.regime == TaxRegime.MICRO) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", null);
            d.put("title", "Годовой отчёт об обороте микробизнеса (форма 256). Срок уточните на taxservice.am");
            list.add(d);
        }
        return list;
    }

    private static void addDeadline(List<Map<String, Object>> list, LocalDate today, LocalDate date, String title) {
        if (date.isBefore(today)) return;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("date", date);
        d.put("title", title);
        list.add(d);
    }
}
