package am.pulsar.tax.app.service.calculation;

import am.pulsar.tax.app.service.calculation.TaxEngine.Calculation;
import am.pulsar.tax.app.service.calculation.TaxEngine.Line;
import am.pulsar.tax.app.service.calculation.TaxEngine.Segment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Почему изменилась сумма: сравнение сохранённого расчёта (снимка) с текущим.
 * Показывает добавленные, удалённые и изменённые операции, смену ставок и разницу итогов.
 */
public final class TaxDiff {

    private TaxDiff() {
    }

    public record LineChange(String kind, Long incomeId, Line before, Line after, BigDecimal amountDelta) {
    }

    public record RateChange(String ruleCode, BigDecimal rateBefore, BigDecimal rateAfter) {
    }

    public record Result(boolean changed, Map<String, BigDecimal[]> totals, List<LineChange> lines, List<RateChange> rates) {
    }

    public static Result compare(Calculation before, Calculation after) {
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();
        put(totals, "turnover", before.totals().turnover(), after.totals().turnover());
        put(totals, "taxBase", before.totals().taxBase(), after.totals().taxBase());
        put(totals, "accrued", before.totals().accrued(), after.totals().accrued());
        put(totals, "paid", before.totals().paid(), after.totals().paid());
        put(totals, "due", before.totals().due(), after.totals().due());

        Map<Long, Line> old = new LinkedHashMap<>();
        for (Line l : before.lines()) old.put(l.incomeId(), l);
        List<LineChange> lines = new ArrayList<>();
        for (Line l : after.lines()) {
            Line o = old.remove(l.incomeId());
            if (o == null) {
                lines.add(new LineChange("ADDED", l.incomeId(), null, l, l.amountAmd()));
            } else if (o.amountAmd().compareTo(l.amountAmd()) != 0 || !o.date().equals(l.date())
                    || !same(o.ratePercent(), l.ratePercent()) || o.inBase() != l.inBase()) {
                lines.add(new LineChange("CHANGED", l.incomeId(), o, l, l.amountAmd().subtract(o.amountAmd())));
            }
        }
        for (Line o : old.values()) lines.add(new LineChange("REMOVED", o.incomeId(), o, null, o.amountAmd().negate()));

        Map<String, BigDecimal> rb = new LinkedHashMap<>();
        for (Segment s : before.segments()) rb.put(s.ruleCode() + "@" + s.ruleFrom(), s.ratePercent());
        Map<String, BigDecimal> ra = new LinkedHashMap<>();
        for (Segment s : after.segments()) ra.put(s.ruleCode() + "@" + s.ruleFrom(), s.ratePercent());
        List<RateChange> rates = new ArrayList<>();
        // Ставка по одному и тому же коду правила стала другой (новая редакция или своя ставка).
        Map<String, BigDecimal> byCodeBefore = new LinkedHashMap<>();
        rb.forEach((k, v) -> byCodeBefore.put(k.substring(0, k.indexOf('@')), v));
        Map<String, BigDecimal> byCodeAfter = new LinkedHashMap<>();
        ra.forEach((k, v) -> byCodeAfter.put(k.substring(0, k.indexOf('@')), v));
        for (String code : union(byCodeBefore, byCodeAfter)) {
            BigDecimal b = byCodeBefore.get(code);
            BigDecimal a = byCodeAfter.get(code);
            if (!same(a, b)) rates.add(new RateChange(code, b, a));
        }

        boolean changed = !lines.isEmpty() || !rates.isEmpty()
                || totals.values().stream().anyMatch(v -> v[2] != null && v[2].signum() != 0)
                || totals.values().stream().anyMatch(v -> (v[0] == null) != (v[1] == null));
        return new Result(changed, totals, lines, rates);
    }

    private static List<String> union(Map<String, ?> a, Map<String, ?> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String k : b.keySet()) if (!keys.contains(k)) keys.add(k);
        return keys;
    }

    private static void put(Map<String, BigDecimal[]> m, String key, BigDecimal before, BigDecimal after) {
        BigDecimal delta = before == null || after == null ? null : after.subtract(before);
        m.put(key, new BigDecimal[]{before, after, delta});
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return Objects.equals(a == null ? null : a.stripTrailingZeros(), b == null ? null : b.stripTrailingZeros());
    }
}
