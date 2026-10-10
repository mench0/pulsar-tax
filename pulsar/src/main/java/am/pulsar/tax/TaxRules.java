package am.pulsar.tax;

import am.pulsar.tax.Model.ActivityType;
import am.pulsar.tax.Model.Profile;
import am.pulsar.tax.Model.TaxRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * Налоговые правила и чистые расчёты.
 *
 * Значения взяты из открытых обзоров на 2026 год (пороги 24 и 115 млн драмов,
 * ставки налога с оборота 10% и 1% для High-Tech) и должны быть сверены
 * с актуальной редакцией Налогового кодекса РА перед использованием с клиентами.
 */
public final class TaxRules {

    public static final BigDecimal MICRO_THRESHOLD = new BigDecimal("24000000");
    public static final BigDecimal TURNOVER_THRESHOLD = new BigDecimal("115000000");
    /** С какого процента от порога показывать предупреждение. */
    public static final int WARNING_PERCENT = 80;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Map<ActivityType, BigDecimal> TURNOVER_RATES = new EnumMap<>(Map.of(
            ActivityType.TRADE, BigDecimal.TEN,
            ActivityType.SERVICES, BigDecimal.TEN,
            ActivityType.HIGH_TECH, BigDecimal.ONE));

    public enum ThresholdStatus { OK, WARNING, EXCEEDED, NONE }

    private TaxRules() {
    }

    /** Ставка в процентах; null, если режим не рассчитывается или ставка не задана. */
    public static BigDecimal ratePercent(Profile p) {
        return switch (p.regime) {
            case MICRO -> BigDecimal.ZERO;
            case TURNOVER -> p.activity == ActivityType.CUSTOM ? p.customRate : TURNOVER_RATES.get(p.activity);
            case GENERAL -> null;
        };
    }

    /** Налог с оборота, округлённый до драма; null, если ставки нет. */
    public static BigDecimal tax(BigDecimal turnover, BigDecimal ratePercent) {
        if (ratePercent == null) return null;
        return turnover.multiply(ratePercent).divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    /** Порог оборота для режима; null у общей системы. */
    public static BigDecimal threshold(TaxRegime regime) {
        return switch (regime) {
            case MICRO -> MICRO_THRESHOLD;
            case TURNOVER -> TURNOVER_THRESHOLD;
            case GENERAL -> null;
        };
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
}
