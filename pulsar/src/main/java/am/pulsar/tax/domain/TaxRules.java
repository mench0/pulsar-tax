package am.pulsar.tax.domain;

import am.pulsar.tax.entity.Business;
import am.pulsar.tax.entity.Enums.ActivityType;
import am.pulsar.tax.entity.Enums.TaxRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Налоговые правила и чистые расчёты.
 *
 * Правила хранятся по годам: для года берётся последняя редакция, вступившая в силу не позже него.
 * Значения взяты из открытых обзоров на 2026 год (пороги 24 и 115 млн драмов,
 * ставки налога с оборота 10% и 1% для High-Tech, списки запрещённых видов деятельности)
 * и должны быть сверены с актуальной редакцией Налогового кодекса РА перед использованием с клиентами.
 */
public final class TaxRules {

    /** Правила одного года. */
    public record YearRules(
            int fromYear,
            BigDecimal microThreshold,
            BigDecimal turnoverThreshold,
            Map<ActivityType, BigDecimal> turnoverRates,
            Set<ActivityType> notMicro,
            Set<ActivityType> notTurnover) {

        /** Порог оборота для режима; null у общей системы. */
        public BigDecimal threshold(TaxRegime regime) {
            return switch (regime) {
                case MICRO -> microThreshold;
                case TURNOVER -> turnoverThreshold;
                case GENERAL -> null;
            };
        }

        /** Может ли этот вид деятельности применять режим. Для своей ставки ограничения не проверяются. */
        public boolean allowed(ActivityType activity, TaxRegime regime) {
            return switch (regime) {
                case MICRO -> !notMicro.contains(activity);
                case TURNOVER -> !notTurnover.contains(activity);
                case GENERAL -> true;
            };
        }

        public List<TaxRegime> allowedRegimes(ActivityType activity) {
            List<TaxRegime> list = new ArrayList<>();
            for (TaxRegime r : TaxRegime.values()) {
                if (allowed(activity, r)) list.add(r);
            }
            return list;
        }
    }

    /** С какого процента от порога показывать предупреждение. */
    public static final int WARNING_PERCENT = 80;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final NavigableMap<Integer, YearRules> RULES = new TreeMap<>();

    static {
        // Ставка 10% для консультаций, инжиниринга, маркетинга, дизайна и перевода — как у услуг; сверить.
        Map<ActivityType, BigDecimal> rates2026 = new EnumMap<>(ActivityType.class);
        for (ActivityType a : List.of(ActivityType.TRADE, ActivityType.SERVICES, ActivityType.CONSULTING,
                ActivityType.ENGINEERING, ActivityType.MARKETING, ActivityType.DESIGN, ActivityType.TRANSLATION)) {
            rates2026.put(a, BigDecimal.TEN);
        }
        rates2026.put(ActivityType.HIGH_TECH, BigDecimal.ONE);
        add(new YearRules(2026,
                new BigDecimal("24000000"),
                new BigDecimal("115000000"),
                rates2026,
                EnumSet.of(ActivityType.CONSULTING, ActivityType.LEGAL, ActivityType.ACCOUNTING,
                        ActivityType.ENGINEERING, ActivityType.MARKETING, ActivityType.DESIGN,
                        ActivityType.TRANSLATION),
                EnumSet.of(ActivityType.LEGAL, ActivityType.ACCOUNTING, ActivityType.AGENCY)));
    }

    public enum ThresholdStatus { OK, WARNING, EXCEEDED, NONE }

    private TaxRules() {
    }

    private static void add(YearRules r) {
        RULES.put(r.fromYear(), r);
    }

    /** Правила для года. Для лет раньше первой редакции берётся первая. */
    public static YearRules forYear(int year) {
        Map.Entry<Integer, YearRules> e = RULES.floorEntry(year);
        return e != null ? e.getValue() : RULES.firstEntry().getValue();
    }

    /** Ставка в процентах; null, если режим не рассчитывается, ставка не задана или режим недоступен. */
    public static BigDecimal ratePercent(Business p, int year) {
        YearRules r = forYear(year);
        return switch (p.getRegime()) {
            case MICRO -> BigDecimal.ZERO;
            case TURNOVER -> p.getActivity() == ActivityType.CUSTOM ? p.getCustomRate() : r.turnoverRates().get(p.getActivity());
            case GENERAL -> null;
        };
    }

    /** Может ли вид деятельности из профиля применять выбранный в нём режим. */
    public static boolean regimeAllowed(Business p, int year) {
        return forYear(year).allowed(p.getActivity(), p.getRegime());
    }

    /** Налог с оборота, округлённый до драма; null, если ставки нет. */
    public static BigDecimal tax(BigDecimal turnover, BigDecimal ratePercent) {
        if (ratePercent == null) return null;
        return turnover.multiply(ratePercent).divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    public static ThresholdStatus status(BigDecimal used, BigDecimal limit) {
        if (limit == null) return ThresholdStatus.NONE;
        if (used.compareTo(limit) > 0) return ThresholdStatus.EXCEEDED;
        BigDecimal warnAt = limit.multiply(BigDecimal.valueOf(WARNING_PERCENT))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
        return used.compareTo(warnAt) >= 0 ? ThresholdStatus.WARNING : ThresholdStatus.OK;
    }

    public static BigDecimal percentOf(BigDecimal used, BigDecimal limit) {
        if (limit == null || limit.signum() == 0) return BigDecimal.ZERO;
        return used.multiply(HUNDRED).divide(limit, 1, RoundingMode.HALF_UP);
    }

    /** Правила года для фронтенда: пороги и для каждого вида деятельности — ставка и доступные режимы. */
    public static Map<String, Object> toJson(int year) {
        YearRules r = forYear(year);
        List<Map<String, Object>> activities = new ArrayList<>();
        for (ActivityType a : ActivityType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("activity", a);
            m.put("turnoverRate", r.turnoverRates().get(a));
            m.put("allowedRegimes", r.allowedRegimes(a));
            activities.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", year);
        m.put("rulesFromYear", r.fromYear());
        m.put("microThreshold", r.microThreshold());
        m.put("turnoverThreshold", r.turnoverThreshold());
        m.put("activities", activities);
        return m;
    }
}
