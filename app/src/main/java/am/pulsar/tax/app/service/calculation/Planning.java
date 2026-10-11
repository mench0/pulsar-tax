package am.pulsar.tax.app.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Прогноз денежных потоков на несколько месяцев: что известно (счета к получению, налоговые сроки)
 * и что оценено по истории (обычные поступления и расходы). Оценка — с диапазоном «плохо / обычно / хорошо»,
 * а не одной цифрой: это не обещание дохода и не точная сумма будущего налога.
 */
public final class Planning {

    private Planning() {
    }

    /** Поток денег одного месяца истории: поступления-доходы и обычные списания (без налогов и своих переводов). */
    public record MonthFlow(YearMonth month, BigDecimal inflow, BigDecimal outflow) {
    }

    /** Ожидаемое поступление по открытому счёту. */
    public record Receivable(Long invoiceId, String number, BigDecimal amount, LocalDate expected, boolean overdue) {
    }

    /** Налоговый платёж со сроком: known — уже начислен, иначе — оценка по прогнозу оборота. */
    public record TaxDue(String period, LocalDate deadline, BigDecimal amount, boolean known) {
    }

    public record Month(YearMonth month, BigDecimal opening, BigDecimal historyInflow, BigDecimal receivables,
                        BigDecimal historyOutflow, BigDecimal taxes, BigDecimal closingLow, BigDecimal closing,
                        BigDecimal closingHigh, List<TaxDue> taxItems, List<Receivable> receivableItems) {
    }

    public record Warning(String code, Map<String, Object> params) {
    }

    public record Forecast(LocalDate from, int months, BigDecimal startingCash, LocalDate startingCashAsOf,
                           int historyMonths, BigDecimal avgInflow, BigDecimal minInflow, BigDecimal maxInflow,
                           BigDecimal avgOutflow, BigDecimal minOutflow, BigDecimal maxOutflow,
                           List<Month> projection, List<Warning> warnings, List<String> assumptions) {
    }

    /**
     * @param history      месяцы истории (полные, без текущего); пустая — прогноз только по известным платежам
     * @param ratePercent  ставка для оценки налога с будущих поступлений; null — оценка не делается
     * @param startingCash остаток денег сейчас (по словам пользователя); null — считаются только потоки от нуля
     */
    public static Forecast forecast(LocalDate today, int months, List<MonthFlow> history, List<Receivable> receivables,
                                    List<TaxDue> knownTaxes, BigDecimal ratePercent, BigDecimal startingCash,
                                    LocalDate startingCashAsOf, BiFunction<Integer, Integer, LocalDate> quarterDeadline) {
        List<String> assumptions = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        BigDecimal avgIn = avg(history.stream().map(MonthFlow::inflow).toList());
        BigDecimal avgOut = avg(history.stream().map(MonthFlow::outflow).toList());
        BigDecimal minIn = history.stream().map(MonthFlow::inflow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxIn = history.stream().map(MonthFlow::inflow).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal minOut = history.stream().map(MonthFlow::outflow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxOut = history.stream().map(MonthFlow::outflow).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (history.isEmpty()) {
            warnings.add(new Warning("NO_HISTORY", Map.of()));
            assumptions.add("ONLY_KNOWN_PAYMENTS");
        } else {
            assumptions.add("HISTORY_AVERAGE");
            if (history.size() < 3) warnings.add(new Warning("SHORT_HISTORY", Map.of("months", history.size())));
        }
        assumptions.add("RECEIVABLES_PAID_ON_DUE_DATE");
        assumptions.add("TAX_ON_CASH_RECEIPTS");
        if (startingCash == null) {
            warnings.add(new Warning("NO_STARTING_CASH", Map.of()));
            assumptions.add("STARTING_CASH_ZERO");
        }

        // Известные налоги — по месяцу срока. Просроченные — в первый месяц прогноза.
        YearMonth first = YearMonth.from(today);
        Map<YearMonth, List<TaxDue>> taxesByMonth = new TreeMap<>();
        for (TaxDue t : knownTaxes) {
            YearMonth m = t.deadline() == null || YearMonth.from(t.deadline()).isBefore(first) ? first : YearMonth.from(t.deadline());
            taxesByMonth.computeIfAbsent(m, k -> new ArrayList<>()).add(t);
            if (t.deadline() != null && t.deadline().isBefore(today)) {
                warnings.add(new Warning("TAX_OVERDUE", Map.of("period", t.period(), "amount", t.amount(), "deadline", t.deadline())));
            }
        }
        Map<YearMonth, List<Receivable>> recByMonth = new TreeMap<>();
        for (Receivable r : receivables) {
            YearMonth m = r.expected() == null || YearMonth.from(r.expected()).isBefore(first) ? first : YearMonth.from(r.expected());
            recByMonth.computeIfAbsent(m, k -> new ArrayList<>()).add(r);
        }
        // Налог с будущих поступлений (история + счета) — оценка, платится в срок за квартал поступления.
        Map<YearMonth, BigDecimal> projectedTurnoverByQuarterEnd = new TreeMap<>();
        if (ratePercent != null) assumptions.add("ESTIMATED_TAX_AT_CURRENT_RATE");

        List<Month> projection = new ArrayList<>();
        BigDecimal opening = startingCash == null ? BigDecimal.ZERO : startingCash;
        BigDecimal openingLow = opening, openingHigh = opening;
        for (int i = 0; i < months; i++) {
            YearMonth m = first.plusMonths(i);
            // Текущий месяц уже идёт: обычные потоки — пропорционально оставшимся дням.
            BigDecimal share = i == 0 ? BigDecimal.valueOf(m.lengthOfMonth() - today.getDayOfMonth() + 1)
                    .divide(BigDecimal.valueOf(m.lengthOfMonth()), 4, RoundingMode.HALF_UP) : BigDecimal.ONE;
            BigDecimal in = avgIn.multiply(share).setScale(0, RoundingMode.HALF_UP);
            BigDecimal out = avgOut.multiply(share).setScale(0, RoundingMode.HALF_UP);
            List<Receivable> recs = recByMonth.getOrDefault(m, List.of());
            BigDecimal rec = recs.stream().map(Receivable::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<TaxDue> taxItems = new ArrayList<>(taxesByMonth.getOrDefault(m, List.of()));

            if (ratePercent != null) {
                YearMonth qEnd = YearMonth.of(m.getYear(), ((m.getMonthValue() - 1) / 3 + 1) * 3);
                projectedTurnoverByQuarterEnd.merge(qEnd, in.add(rec), BigDecimal::add);
            }
            // Оценка налога за закончившийся квартал прогноза — в месяц его срока.
            for (Map.Entry<YearMonth, BigDecimal> e : projectedTurnoverByQuarterEnd.entrySet()) {
                LocalDate deadline = quarterDeadline.apply(e.getKey().getYear(), e.getKey().getMonthValue() / 3);
                if (YearMonth.from(deadline).equals(m) && e.getValue().signum() > 0) {
                    BigDecimal est = e.getValue().multiply(ratePercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                    if (est.signum() > 0) taxItems.add(new TaxDue(e.getKey().getYear() + "-Q" + (e.getKey().getMonthValue() / 3), deadline, est, false));
                }
            }
            BigDecimal taxes = taxItems.stream().map(TaxDue::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal closing = opening.add(in).add(rec).subtract(out).subtract(taxes);
            // Плохой сценарий: минимум поступлений из истории, максимум расходов, просроченные счета не оплачены.
            BigDecimal recLow = recs.stream().filter(r -> !r.overdue()).map(Receivable::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal low = openingLow.add(minIn.multiply(share)).add(recLow).subtract(maxOut.multiply(share)).subtract(taxes)
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal high = openingHigh.add(maxIn.multiply(share)).add(rec).subtract(minOut.multiply(share)).subtract(taxes)
                    .setScale(0, RoundingMode.HALF_UP);
            projection.add(new Month(m, opening, in, rec, out, taxes, low, closing, high, taxItems, recs));
            if (taxes.signum() > 0 && low.signum() < 0) {
                warnings.add(new Warning("POSSIBLE_SHORTFALL_FOR_TAX", Map.of("month", m.toString(), "taxes", taxes, "closingLow", low)));
            } else if (closing.signum() < 0) {
                warnings.add(new Warning("NEGATIVE_BALANCE", Map.of("month", m.toString(), "closing", closing)));
            }
            opening = closing;
            openingLow = low;
            openingHigh = high;
        }
        return new Forecast(today, months, startingCash, startingCashAsOf, history.size(), avgIn, minIn, maxIn,
                avgOut, minOut, maxOut, projection, warnings, assumptions);
    }

    private static BigDecimal avg(List<BigDecimal> v) {
        if (v.isEmpty()) return BigDecimal.ZERO;
        return v.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(v.size()), 0, RoundingMode.HALF_UP);
    }
}
