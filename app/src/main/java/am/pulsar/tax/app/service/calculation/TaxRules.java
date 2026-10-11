package am.pulsar.tax.app.service.calculation;

import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.service.calculation.LegalBasis.Status;
import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.TaxRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * <p>Правила хранятся редакциями с датой вступления в силу: к операции применяется редакция,
 * действовавшая на её дату. Для дат раньше первой известной редакции правил нет — расчёт
 * за такой период не выполняется и явно помечается (мы не переносим новые ставки на прошлое).
 *
 * <p>У каждого значения есть основание ({@link LegalBasis}) и статус проверки. Официальная база
 * arlis.am при подготовке этих правил была недоступна, поэтому значения сверены по официальному
 * порталу госуслуг (hartak.am) и профессиональным обзорам; где источники расходятся — статус UNCONFIRMED.
 */
public final class TaxRules {

    /** Коды правил: по ним фронтенд показывает название, а объяснение расчёта — основание. */
    public static final String MICRO_THRESHOLD = "MICRO_THRESHOLD";
    public static final String TURNOVER_THRESHOLD = "TURNOVER_THRESHOLD";
    public static final String TURNOVER_RATE = "TURNOVER_RATE";
    public static final String TURNOVER_BASE = "TURNOVER_BASE";
    public static final String MICRO_TAX = "MICRO_TAX";
    public static final String ROUNDING = "ROUNDING";
    public static final String TURNOVER_DEADLINE = "TURNOVER_DEADLINE";
    public static final String REGIME_CHOICE_DEADLINE = "REGIME_CHOICE_DEADLINE";
    public static final String ALLOWED_ACTIVITIES = "ALLOWED_ACTIVITIES";
    public static final String CUSTOM_RATE = "CUSTOM_RATE";

    /** Редакция правил, действующая с effectiveFrom до следующей редакции. */
    public record YearRules(
            LocalDate effectiveFrom,
            BigDecimal microThreshold,
            BigDecimal turnoverThreshold,
            Map<ActivityType, BigDecimal> turnoverRates,
            Set<ActivityType> notMicro,
            Set<ActivityType> notTurnover,
            Map<String, LegalBasis> basis) {

        public int fromYear() {
            return effectiveFrom.getYear();
        }

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

        /** Основание правила; для ставки — по виду деятельности (TURNOVER_RATE.SERVICES). */
        public LegalBasis basisOf(String code) {
            LegalBasis b = basis.get(code);
            if (b == null && code.contains(".")) b = basis.get(code.substring(0, code.indexOf('.')));
            return b != null ? b : new LegalBasis(LegalBasis.TAX_CODE, LegalBasis.TAX_CODE_URL, Status.UNCONFIRMED,
                    "Основание не указано");
        }
    }

    /** С какого процента от порога показывать предупреждение. */
    public static final int WARNING_PERCENT = 80;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final NavigableMap<LocalDate, YearRules> RULES = new TreeMap<>();

    private static final String HARTAK_2025 = "https://hartak.am/news/e5015ab2-3a5f-44ce-86e5-105dc36434ab/";

    static {
        Map<ActivityType, BigDecimal> rates = new EnumMap<>(ActivityType.class);
        rates.put(ActivityType.TRADE, BigDecimal.TEN);
        for (ActivityType a : List.of(ActivityType.SERVICES, ActivityType.CONSULTING, ActivityType.ENGINEERING,
                ActivityType.MARKETING, ActivityType.DESIGN, ActivityType.TRANSLATION)) {
            rates.put(a, BigDecimal.TEN);
        }
        rates.put(ActivityType.HIGH_TECH, BigDecimal.ONE);

        Map<String, LegalBasis> basis = new LinkedHashMap<>();
        String code = LegalBasis.TAX_CODE;
        String url = LegalBasis.TAX_CODE_URL;
        basis.put(TURNOVER_RATE + "." + ActivityType.TRADE, new LegalBasis(code + ", гл. «Налог с оборота» (ставки, в ред. с 01.01.2025)",
                HARTAK_2025, Status.OFFICIAL_SUMMARY,
                "Ставка 10% для торговли с 1 января 2025 года — по сообщению портала госуслуг hartak.am"));
        basis.put(TURNOVER_RATE + "." + ActivityType.SERVICES, new LegalBasis(code + ", гл. «Налог с оборота» (ставки, в ред. с 01.01.2025)",
                url, Status.SECONDARY_SOURCES,
                "10% — по обзорам 2025–2026 годов; старая страница hartak.am указывала 5% (до изменений 2025 года)"));
        for (ActivityType a : List.of(ActivityType.CONSULTING, ActivityType.ENGINEERING, ActivityType.MARKETING,
                ActivityType.DESIGN, ActivityType.TRANSLATION)) {
            basis.put(TURNOVER_RATE + "." + a, new LegalBasis(code + ", гл. «Налог с оборота»", url, Status.UNCONFIRMED,
                    "Pulsar относит этот вид деятельности к услугам (10%); отнесение не сверено с кодексом"));
        }
        basis.put(TURNOVER_RATE + "." + ActivityType.HIGH_TECH, new LegalBasis(code + ", гл. «Налог с оборота» (сфера высоких технологий)",
                url, Status.SECONDARY_SOURCES,
                "1% — по обзорам; применяется при выполнении условий для сферы высоких технологий, условия не проверяются"));
        basis.put(CUSTOM_RATE, new LegalBasis("Ставка, указанная пользователем", null, Status.UNCONFIRMED,
                "Pulsar не проверяет ставку, введённую вручную"));
        basis.put(MICRO_THRESHOLD, new LegalBasis(code + ", гл. «Микропредпринимательство»", url, Status.SECONDARY_SOURCES,
                "24 млн драмов оборота за год — согласно обзорам 2025–2026 годов"));
        basis.put(TURNOVER_THRESHOLD, new LegalBasis(code + ", гл. «Налог с оборота»", url, Status.SECONDARY_SOURCES,
                "115 млн драмов — в большинстве обзоров; в одном указано 120 млн"));
        basis.put(TURNOVER_BASE, new LegalBasis(code + ", гл. «Налог с оборота» (объект налогообложения)", url, Status.SECONDARY_SOURCES,
                "Налогом облагается оборот (выручка); расходы базу не уменьшают"));
        basis.put(MICRO_TAX, new LegalBasis(code + ", гл. «Микропредпринимательство»", url, Status.SECONDARY_SOURCES,
                "Микропредприниматель освобождён от налога на прибыль, НДС и налога с оборота по этой деятельности; "
                        + "подоходный налог и соцплатежи за работников не учитываются"));
        basis.put(ROUNDING, new LegalBasis(code, url, Status.UNCONFIRMED,
                "Pulsar округляет налог за период до целого драма по правилам математики; порядок не сверен"));
        basis.put(TURNOVER_DEADLINE, new LegalBasis(code + ", гл. «Налог с оборота» (отчётный период, сроки)", url, Status.SECONDARY_SOURCES,
                "Квартальный расчёт и уплата — не позднее 20-го числа месяца, следующего за кварталом"));
        basis.put(REGIME_CHOICE_DEADLINE, new LegalBasis(code, url, Status.SECONDARY_SOURCES,
                "Заявление о переходе на налог с оборота или микропредпринимательство — до 20 февраля"));
        basis.put(ALLOWED_ACTIVITIES, new LegalBasis(code, url, Status.UNCONFIRMED,
                "Списки видов деятельности, которым режим недоступен, составлены по обзорам и упрощены"));

        add(new YearRules(LocalDate.of(2025, 1, 1),
                new BigDecimal("24000000"),
                new BigDecimal("115000000"),
                Map.copyOf(rates),
                EnumSet.of(ActivityType.CONSULTING, ActivityType.LEGAL, ActivityType.ACCOUNTING,
                        ActivityType.ENGINEERING, ActivityType.MARKETING, ActivityType.DESIGN,
                        ActivityType.TRANSLATION),
                EnumSet.of(ActivityType.LEGAL, ActivityType.ACCOUNTING, ActivityType.AGENCY),
                Map.copyOf(basis)));
    }

    public enum ThresholdStatus { OK, WARNING, EXCEEDED, NONE }

    private TaxRules() {
    }

    private static void add(YearRules r) {
        RULES.put(r.effectiveFrom(), r);
    }

    /** Редакция, действующая на дату; null, если на эту дату правил в Pulsar нет. */
    public static YearRules forDate(LocalDate date) {
        Map.Entry<LocalDate, YearRules> e = RULES.floorEntry(date);
        return e == null ? null : e.getValue();
    }

    /** Редакция на конец года (последняя вступившая в силу в этом году); null, если правил за год нет. */
    public static YearRules forYear(int year) {
        return forDate(LocalDate.of(year, 12, 31));
    }

    /** Первая дата, с которой у Pulsar есть правила. */
    public static LocalDate firstKnownDate() {
        return RULES.firstKey();
    }

    /** Ставка в процентах на дату; null, если режим не рассчитывается, ставка не задана или правил нет. */
    public static BigDecimal ratePercent(Business p, LocalDate date) {
        YearRules r = forDate(date);
        if (r == null) return null;
        return switch (p.getRegime()) {
            case MICRO -> BigDecimal.ZERO;
            case TURNOVER -> p.getActivity() == ActivityType.CUSTOM ? p.getCustomRate() : r.turnoverRates().get(p.getActivity());
            case GENERAL -> null;
        };
    }

    /** Ставка на конец года. */
    public static BigDecimal ratePercent(Business p, int year) {
        return ratePercent(p, LocalDate.of(year, 12, 31));
    }

    /** Код правила ставки для профиля: TURNOVER_RATE.TRADE или CUSTOM_RATE. */
    public static String rateCode(Business p) {
        return p.getActivity() == ActivityType.CUSTOM ? CUSTOM_RATE : TURNOVER_RATE + "." + p.getActivity();
    }

    /** Может ли вид деятельности из профиля применять выбранный в нём режим. Без правил — считаем допустимым. */
    public static boolean regimeAllowed(Business p, int year) {
        YearRules r = forYear(year);
        return r == null || r.allowed(p.getActivity(), p.getRegime());
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

    /** Правила года для фронтенда: пороги, ставки, доступные режимы и основания с их статусом. */
    public static Map<String, Object> toJson(int year) {
        YearRules r = forYear(year);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", year);
        if (r == null) {
            m.put("rulesFromYear", null);
            m.put("rulesMissing", true);
            m.put("firstKnownDate", firstKnownDate());
            m.put("activities", List.of());
            return m;
        }
        List<Map<String, Object>> activities = new ArrayList<>();
        for (ActivityType a : ActivityType.values()) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("activity", a);
            am.put("turnoverRate", r.turnoverRates().get(a));
            am.put("allowedRegimes", r.allowedRegimes(a));
            activities.add(am);
        }
        m.put("rulesFromYear", r.fromYear());
        m.put("effectiveFrom", r.effectiveFrom());
        m.put("microThreshold", r.microThreshold());
        m.put("turnoverThreshold", r.turnoverThreshold());
        m.put("activities", activities);
        m.put("basis", r.basis());
        return m;
    }
}
