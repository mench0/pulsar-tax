package am.pulsar.tax.app.service.calculation;

import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.service.calculation.TaxRules.YearRules;
import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.TaxRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Налоговый расчёт за период (квартал или год) с полным объяснением.
 *
 * <p>Разделяются: оборот (все поступления-доходы в драмах), расходы (только справочно),
 * налоговая база, начисленный налог, уплаченный налог и остаток к уплате (налоговый резерв).
 * Ставка берётся по дате каждой операции: если правила меняются внутри периода, налог считается
 * по частям и округляется один раз — для периода целиком. Все суммы — BigDecimal без двоичной арифметики.
 */
public final class TaxEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Допустимое расхождение курса в операции с курсом ЦБ РА на ту же дату. */
    private static final BigDecimal RATE_TOLERANCE_PERCENT = new BigDecimal("0.5");

    private TaxEngine() {
    }

    /** Итоги периода. due — остаток к уплате, он же рекомендуемый налоговый резерв по начисленному налогу. */
    public record Totals(BigDecimal turnover, BigDecimal expenses, BigDecimal taxBase,
                         BigDecimal taxUnrounded, BigDecimal rounding, BigDecimal accrued,
                         BigDecimal paid, BigDecimal due, BigDecimal overpaid) {
    }

    /** Операция дохода в расчёте: как получена сумма в драмах и какая ставка к ней применена. */
    public record Line(Long incomeId, LocalDate date, String client, String description,
                       Currency currency, BigDecimal originalAmount, BigDecimal fxRate, String fxCheck,
                       BigDecimal amountAmd, BigDecimal ratePercent, String ruleCode, LocalDate ruleFrom,
                       boolean inBase, String exclusionReason) {
    }

    /** Часть периода с одной ставкой: база × ставка = налог до округления. */
    public record Segment(LocalDate ruleFrom, String ruleCode, BigDecimal ratePercent, BigDecimal base,
                          BigDecimal taxUnrounded, int operations) {
    }

    /** Применённое правило с основанием. */
    public record AppliedRule(String code, String value, LocalDate effectiveFrom, LegalBasis basis) {
    }

    /** Шаг объяснения: код (текст собирает фронтенд), параметры и сумма шага. */
    public record Step(String code, Map<String, Object> params, BigDecimal amount) {
    }

    /** Замечание к расчёту. ERROR — расчёт неполный или неверный, WARNING — нужно проверить, INFO — справка. */
    public record Issue(String severity, String code, Map<String, Object> params) {
    }

    public record Calculation(int year, Integer quarter, LocalDate periodFrom, LocalDate periodTo,
                              TaxRegime regime, ActivityType activity, boolean calculable,
                              Totals totals, List<Segment> segments, List<Line> lines,
                              List<Step> steps, List<AppliedRule> rules, List<Issue> issues) {

        public boolean hasErrors() {
            return issues.stream().anyMatch(i -> "ERROR".equals(i.severity()));
        }
    }

    /**
     * Расчёт за квартал (quarter 1–4) или год (quarter = null).
     *
     * @param incomes  доходы ИП (берутся только попавшие в период)
     * @param payments уплаты налога за год (quarter 0 — за год целиком)
     * @param cbaRate  курс ЦБ РА на дату из кэша или null, если неизвестен — для проверки курса в операциях
     */
    public static Calculation calculate(Business profile, int year, Integer quarter,
                                        List<Income> incomes, List<Expense> expenses, List<TaxPayment> payments,
                                        BiFunction<LocalDate, Currency, BigDecimal> cbaRate) {
        LocalDate from = quarter == null ? LocalDate.of(year, 1, 1) : LocalDate.of(year, quarter * 3 - 2, 1);
        LocalDate to = quarter == null ? LocalDate.of(year, 12, 31) : from.plusMonths(3).minusDays(1);
        List<Issue> issues = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        Map<String, AppliedRule> rules = new LinkedHashMap<>();
        TaxRegime regime = profile.getRegime();

        List<Income> inPeriod = incomes.stream()
                .filter(i -> !i.getDate().isBefore(from) && !i.getDate().isAfter(to))
                .sorted(Comparator.comparing(Income::getDate).thenComparing(Income::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        BigDecimal expenseSum = expenses.stream()
                .filter(e -> !e.getDate().isBefore(from) && !e.getDate().isAfter(to))
                .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean calculable = regime != TaxRegime.GENERAL;
        if (!calculable) issues.add(issue("ERROR", "GENERAL_NOT_SUPPORTED", Map.of()));
        if (TaxRules.forDate(from) == null) {
            issues.add(issue("ERROR", "RULES_MISSING_FOR_PERIOD", Map.of("firstKnownDate", TaxRules.firstKnownDate())));
        }
        YearRules yearRules = TaxRules.forYear(year);
        if (yearRules != null && !yearRules.allowed(profile.getActivity(), regime)) {
            issues.add(issue("ERROR", "REGIME_NOT_ALLOWED", Map.of("activity", profile.getActivity(), "regime", regime)));
            rules.putIfAbsent(TaxRules.ALLOWED_ACTIVITIES, rule(yearRules, TaxRules.ALLOWED_ACTIVITIES, "—"));
        }

        // Строки: сумма в драмах, проверка курса и ставка по дате операции.
        List<Line> lines = new ArrayList<>();
        Map<String, BigDecimal[]> segmentSums = new LinkedHashMap<>(); // ключ: дата редакции|код|ставка → {база, число операций}
        Map<String, Segment> segmentMeta = new LinkedHashMap<>();
        BigDecimal turnover = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        for (Income i : inPeriod) {
            String fxCheck = fxCheck(i, cbaRate, issues);
            BigDecimal amd = i.getAmount();
            turnover = turnover.add(amd);
            YearRules r = TaxRules.forDate(i.getDate());
            BigDecimal rate = r == null ? null : TaxRules.ratePercent(profile, i.getDate());
            String code = regime == TaxRegime.MICRO ? TaxRules.MICRO_TAX : TaxRules.rateCode(profile);
            String exclusion = null;
            if (!calculable) exclusion = "GENERAL_NOT_SUPPORTED";
            else if (r == null) exclusion = "RULES_MISSING_FOR_PERIOD";
            else if (rate == null) exclusion = "RATE_MISSING";
            boolean inBase = exclusion == null;
            lines.add(new Line(i.getId(), i.getDate(), i.getClient(), i.getDescription(), i.getCurrency(),
                    i.getOriginalAmount(), i.getRate(), fxCheck, amd, rate, code, r == null ? null : r.effectiveFrom(),
                    inBase, exclusion));
            if (!inBase) continue;
            base = base.add(amd);
            String key = r.effectiveFrom() + "|" + code + "|" + rate.toPlainString();
            segmentSums.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] s = segmentSums.get(key);
            s[0] = s[0].add(amd);
            s[1] = s[1].add(BigDecimal.ONE);
            segmentMeta.putIfAbsent(key, new Segment(r.effectiveFrom(), code, rate, null, null, 0));
            rules.putIfAbsent(code + "@" + r.effectiveFrom(), rule(r, code, rate.stripTrailingZeros().toPlainString() + "%"));
        }
        boolean rateMissing = calculable && lines.stream().anyMatch(l -> "RATE_MISSING".equals(l.exclusionReason()));
        if (rateMissing || (calculable && regime == TaxRegime.TURNOVER && lines.isEmpty()
                && TaxRules.forDate(to) != null && TaxRules.ratePercent(profile, to) == null)) {
            issues.add(issue("ERROR", "RATE_MISSING", Map.of("activity", profile.getActivity())));
        }

        List<Segment> segments = new ArrayList<>();
        BigDecimal taxUnrounded = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : segmentSums.entrySet()) {
            Segment meta = segmentMeta.get(e.getKey());
            BigDecimal segBase = e.getValue()[0];
            // Точное значение: ставка в процентах с не более чем 4 знаками, деление на 100 конечно.
            BigDecimal segTax = segBase.multiply(meta.ratePercent()).divide(HUNDRED);
            segments.add(new Segment(meta.ruleFrom(), meta.ruleCode(), meta.ratePercent(), segBase, segTax,
                    e.getValue()[1].intValue()));
            taxUnrounded = taxUnrounded.add(segTax);
        }

        // Расходы показываются, но базу не уменьшают: оба режима облагают оборот, а не прибыль.
        steps.add(new Step("TURNOVER_SUM", Map.of("operations", inPeriod.size()), turnover));
        if (expenseSum.signum() > 0) {
            steps.add(new Step("EXPENSES_NOT_DEDUCTIBLE", Map.of("regime", regime), expenseSum));
            if (yearRules != null) rules.putIfAbsent(TaxRules.TURNOVER_BASE, rule(yearRules, TaxRules.TURNOVER_BASE, "—"));
        }
        BigDecimal excluded = turnover.subtract(base);
        if (excluded.signum() > 0) steps.add(new Step("EXCLUDED_FROM_BASE", Map.of(), excluded));
        steps.add(new Step("TAX_BASE", Map.of(), base));
        for (Segment s : segments) {
            steps.add(new Step("RATE_APPLIED", Map.of("rate", s.ratePercent(), "base", s.base(),
                    "rule", s.ruleCode(), "from", s.ruleFrom()), s.taxUnrounded()));
        }
        // Если хоть одна операция не вошла в базу из-за нехватки правил, итог неполный — налог не показываем.
        boolean incomplete = !calculable || rateMissing || TaxRules.forDate(from) == null
                || lines.stream().anyMatch(l -> !l.inBase());
        BigDecimal accrued = incomplete ? null : taxUnrounded.setScale(0, RoundingMode.HALF_UP);
        BigDecimal rounding = accrued == null ? BigDecimal.ZERO : accrued.subtract(taxUnrounded);
        if (accrued != null && rounding.signum() != 0) {
            steps.add(new Step("ROUNDING", Map.of("unrounded", taxUnrounded), rounding));
            YearRules r = TaxRules.forDate(to) != null ? TaxRules.forDate(to) : yearRules;
            if (r != null) rules.putIfAbsent(TaxRules.ROUNDING, rule(r, TaxRules.ROUNDING, "до 1 ֏"));
        }
        if (regime == TaxRegime.MICRO && yearRules != null) {
            rules.putIfAbsent(TaxRules.MICRO_TAX, rule(yearRules, TaxRules.MICRO_TAX, "0%"));
        }
        if (accrued != null) steps.add(new Step("ACCRUED", Map.of(), accrued));

        BigDecimal paid = BigDecimal.ZERO;
        for (TaxPayment p : payments) {
            if (p.getYear() != year) continue;
            boolean forPeriod = quarter == null || p.getQuarter() == quarter;
            if (forPeriod) paid = paid.add(p.getAmount());
        }
        steps.add(new Step("PAID", Map.of(), paid));
        BigDecimal due = accrued == null ? null : accrued.subtract(paid).max(BigDecimal.ZERO);
        BigDecimal overpaid = accrued == null ? BigDecimal.ZERO : paid.subtract(accrued).max(BigDecimal.ZERO);
        if (due != null) steps.add(new Step("DUE", Map.of(), due));
        if (overpaid.signum() > 0) issues.add(issue("WARNING", "OVERPAID", Map.of("amount", overpaid)));

        // Порог считается по обороту с начала года до конца периода.
        if (yearRules != null) {
            BigDecimal limit = yearRules.threshold(regime);
            if (limit != null) {
                BigDecimal ytd = incomes.stream()
                        .filter(i -> i.getDate().getYear() == year && !i.getDate().isAfter(to))
                        .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                String thresholdCode = regime == TaxRegime.MICRO ? TaxRules.MICRO_THRESHOLD : TaxRules.TURNOVER_THRESHOLD;
                rules.putIfAbsent(thresholdCode, rule(yearRules, thresholdCode, limit.toPlainString() + " ֏"));
                TaxRules.ThresholdStatus st = TaxRules.status(ytd, limit);
                if (st == TaxRules.ThresholdStatus.EXCEEDED) {
                    issues.add(issue("ERROR", "THRESHOLD_EXCEEDED", Map.of("turnover", ytd, "limit", limit)));
                } else if (st == TaxRules.ThresholdStatus.WARNING) {
                    issues.add(issue("WARNING", "THRESHOLD_WARNING", Map.of("turnover", ytd, "limit", limit)));
                }
            }
        }

        // Каждое правило, не сверенное с текстом закона, — отдельное предупреждение с его кодом.
        for (AppliedRule r : rules.values()) {
            if (r.basis().needsConfirmation()) {
                issues.add(issue(r.basis().status() == LegalBasis.Status.UNCONFIRMED ? "WARNING" : "INFO",
                        "RULE_NOT_VERIFIED", Map.of("rule", r.code(), "status", r.basis().status())));
            }
        }

        Totals totals = new Totals(turnover, expenseSum, base, taxUnrounded, rounding, accrued, paid, due, overpaid);
        return new Calculation(year, quarter, from, to, regime, profile.getActivity(), accrued != null, totals,
                segments, lines, steps, List.copyOf(rules.values()), issues);
    }

    /**
     * Проверка суммы в валюте: есть ли курс, совпадает ли пересчёт и курс ЦБ РА на дату.
     * Возвращает AMD, OK, CBA_MATCH, CBA_MISMATCH, CBA_UNKNOWN, NO_RATE или AMOUNT_MISMATCH.
     */
    static String fxCheck(Income i, BiFunction<LocalDate, Currency, BigDecimal> cbaRate, List<Issue> issues) {
        if (i.getCurrency() == null || i.getCurrency() == Currency.AMD) return "AMD";
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("incomeId", i.getId());
        ref.put("date", i.getDate());
        ref.put("currency", i.getCurrency());
        if (i.getRate() == null || i.getOriginalAmount() == null) {
            issues.add(issue("ERROR", "FX_RATE_MISSING", ref));
            return "NO_RATE";
        }
        BigDecimal expected = i.getOriginalAmount().multiply(i.getRate()).setScale(2, RoundingMode.HALF_UP);
        if (expected.subtract(i.getAmount()).abs().compareTo(BigDecimal.ONE) > 0) {
            Map<String, Object> p = new LinkedHashMap<>(ref);
            p.put("expected", expected);
            p.put("amount", i.getAmount());
            issues.add(issue("ERROR", "FX_AMOUNT_MISMATCH", p));
            return "AMOUNT_MISMATCH";
        }
        BigDecimal cba = cbaRate == null ? null : cbaRate.apply(i.getDate(), i.getCurrency());
        if (cba == null) return "CBA_UNKNOWN";
        BigDecimal diff = i.getRate().subtract(cba).abs().multiply(HUNDRED).divide(cba, 4, RoundingMode.HALF_UP);
        if (diff.compareTo(RATE_TOLERANCE_PERCENT) > 0) {
            Map<String, Object> p = new LinkedHashMap<>(ref);
            p.put("rate", i.getRate());
            p.put("cbaRate", cba);
            issues.add(issue("WARNING", "FX_RATE_DIFFERS_FROM_CBA", p));
            return "CBA_MISMATCH";
        }
        return "CBA_MATCH";
    }

    private static AppliedRule rule(YearRules r, String code, String value) {
        return new AppliedRule(code, value, r.effectiveFrom(), r.basisOf(code));
    }

    static Issue issue(String severity, String code, Map<String, Object> params) {
        return new Issue(severity, code, params);
    }
}
