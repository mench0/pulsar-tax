package am.pulsar.tax.app.service.calculation;

import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.service.calculation.TaxEngine.Calculation;
import am.pulsar.tax.app.service.calculation.TaxEngine.Issue;
import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.TaxRegime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Налоговый движок: база, ставки по датам, округление, валюта, замечания и сравнение расчётов. */
class TaxEngineTest {

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    static Business profile(TaxRegime regime, ActivityType activity) {
        return Business.builder().id(1L).ownerId(1L).regime(regime).activity(activity).build();
    }

    static long ids = 1;

    static Income income(String date, String amd) {
        return Income.builder().id(ids++).businessId(1L).date(LocalDate.parse(date)).amount(d(amd)).build();
    }

    static Income usd(String date, String original, String rate, String amd) {
        Income i = income(date, amd);
        i.setCurrency(Currency.USD);
        i.setOriginalAmount(d(original));
        i.setRate(rate == null ? null : d(rate));
        return i;
    }

    static Expense expense(String date, String amount) {
        return Expense.builder().id(ids++).businessId(1L).date(LocalDate.parse(date)).amount(d(amount)).build();
    }

    static TaxPayment payment(int year, int quarter, String amount) {
        return TaxPayment.builder().id(ids++).businessId(1L).year(year).quarter(quarter)
                .date(LocalDate.of(year, 4, 15)).amount(d(amount)).build();
    }

    static boolean has(Calculation c, String severity, String code) {
        return c.issues().stream().anyMatch(i -> i.severity().equals(severity) && i.code().equals(code));
    }

    @Test
    void turnoverQuarterSeparatesTurnoverBaseTaxPaidAndDue() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.TURNOVER, ActivityType.SERVICES), 2026, 1,
                List.of(income("2026-01-10", "1000000"), income("2026-03-31", "333333.33"), income("2026-04-01", "999")),
                List.of(expense("2026-02-01", "500000")),
                List.of(payment(2026, 1, "100000"), payment(2026, 2, "7")), null);
        assertTrue(c.calculable());
        assertEquals(2, c.lines().size()); // 1 апреля — уже второй квартал
        assertEquals(d("1333333.33"), c.totals().turnover());
        assertEquals(d("500000"), c.totals().expenses());
        assertEquals(d("1333333.33"), c.totals().taxBase()); // расходы базу не уменьшают
        assertEquals(0, d("133333.333").compareTo(c.totals().taxUnrounded()));
        assertEquals(d("133333"), c.totals().accrued());
        assertEquals(0, d("-0.333").compareTo(c.totals().rounding()));
        assertEquals(d("100000"), c.totals().paid());
        assertEquals(d("33333"), c.totals().due());
        assertTrue(c.steps().stream().anyMatch(s -> s.code().equals("EXPENSES_NOT_DEDUCTIBLE")));
        assertTrue(c.steps().stream().anyMatch(s -> s.code().equals("ROUNDING")));
        // Ставка 10% для услуг не сверена с текстом кодекса — пользователь видит это в замечаниях.
        assertTrue(c.rules().stream().anyMatch(r -> r.code().equals("TURNOVER_RATE.SERVICES")
                && r.basis().status() == LegalBasis.Status.SECONDARY_SOURCES));
        assertTrue(has(c, "INFO", "RULE_NOT_VERIFIED"));
        assertFalse(c.hasErrors());
    }

    @Test
    void sumOfLinesIsRoundedOnceForThePeriod() {
        // 10 операций по 0,05 ֏ налога: при округлении каждой вышло бы 0, а для периода 0,5 → 1.
        List<Income> incomes = new ArrayList<>();
        for (int i = 0; i < 10; i++) incomes.add(income("2026-05-0" + (i % 9 + 1), "0.50"));
        Calculation c = TaxEngine.calculate(profile(TaxRegime.TURNOVER, ActivityType.TRADE), 2026, 2, incomes, List.of(), List.of(), null);
        assertEquals(d("1"), c.totals().accrued());
    }

    @Test
    void microPaysZeroAndExplainsWhy() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.MICRO, ActivityType.SERVICES), 2026, null,
                List.of(income("2026-02-01", "5000000")), List.of(), List.of(), null);
        assertEquals(d("0"), c.totals().accrued());
        assertEquals(d("5000000"), c.totals().turnover());
        assertTrue(c.rules().stream().anyMatch(r -> r.code().equals("MICRO_TAX")));
        assertTrue(c.rules().stream().anyMatch(r -> r.code().equals("MICRO_THRESHOLD")));
    }

    @Test
    void generalRegimeIsNotCalculated() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.GENERAL, ActivityType.TRADE), 2026, 1,
                List.of(income("2026-02-01", "100")), List.of(), List.of(), null);
        assertFalse(c.calculable());
        assertNull(c.totals().accrued());
        assertNull(c.totals().due());
        assertTrue(has(c, "ERROR", "GENERAL_NOT_SUPPORTED"));
    }

    @Test
    void newRulesAreNotAppliedToPeriodsBeforeThem() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.TURNOVER, ActivityType.TRADE), 2024, 4,
                List.of(income("2024-11-01", "100000")), List.of(), List.of(), null);
        assertFalse(c.calculable());
        assertNull(c.totals().accrued());
        assertEquals("RULES_MISSING_FOR_PERIOD", c.lines().getFirst().exclusionReason());
        assertTrue(has(c, "ERROR", "RULES_MISSING_FOR_PERIOD"));
    }

    @Test
    void customActivityWithoutRateIsAnError() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.TURNOVER, ActivityType.CUSTOM), 2026, 1,
                List.of(income("2026-02-01", "100")), List.of(), List.of(), null);
        assertNull(c.totals().accrued());
        assertTrue(has(c, "ERROR", "RATE_MISSING"));

        Business custom = profile(TaxRegime.TURNOVER, ActivityType.CUSTOM);
        custom.setCustomRate(d("3.5"));
        Calculation ok = TaxEngine.calculate(custom, 2026, 1, List.of(income("2026-02-01", "1000")), List.of(), List.of(), null);
        assertEquals(d("35"), ok.totals().accrued());
        assertTrue(has(ok, "WARNING", "RULE_NOT_VERIFIED")); // своя ставка — без проверки
    }

    @Test
    void foreignCurrencyIsCheckedAgainstCbaRate() {
        Income noRate = usd("2026-01-05", "100", null, "38750");
        Income wrongAmount = usd("2026-01-06", "100", "387.5", "40000");
        Income differs = usd("2026-01-07", "100", "400", "40000");
        Income matches = usd("2026-01-08", "100", "387.5", "38750");
        Calculation c = TaxEngine.calculate(profile(TaxRegime.TURNOVER, ActivityType.TRADE), 2026, 1,
                List.of(noRate, wrongAmount, differs, matches), List.of(), List.of(),
                (date, cur) -> d("387.5"));
        assertEquals("NO_RATE", c.lines().get(0).fxCheck());
        assertEquals("AMOUNT_MISMATCH", c.lines().get(1).fxCheck());
        assertEquals("CBA_MISMATCH", c.lines().get(2).fxCheck());
        assertEquals("CBA_MATCH", c.lines().get(3).fxCheck());
        assertTrue(has(c, "ERROR", "FX_RATE_MISSING"));
        assertTrue(has(c, "ERROR", "FX_AMOUNT_MISMATCH"));
        assertTrue(has(c, "WARNING", "FX_RATE_DIFFERS_FROM_CBA"));
    }

    @Test
    void thresholdAndOverpayment() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.MICRO, ActivityType.SERVICES), 2026, 2,
                List.of(income("2026-01-10", "20000000"), income("2026-05-10", "4000001")), List.of(),
                List.of(payment(2026, 2, "10")), null);
        assertTrue(has(c, "ERROR", "THRESHOLD_EXCEEDED")); // порог — с начала года
        assertTrue(has(c, "WARNING", "OVERPAID"));
        assertEquals(d("10"), c.totals().overpaid());
    }

    @Test
    void diffExplainsWhyTheAmountChanged() {
        Business p = profile(TaxRegime.TURNOVER, ActivityType.TRADE);
        Income a = income("2026-01-10", "1000");
        Income b = income("2026-01-11", "2000");
        Income c = income("2026-01-12", "3000");
        Calculation before = TaxEngine.calculate(p, 2026, 1, List.of(a, b), List.of(), List.of(), null);
        Income b2 = income("2026-01-11", "2500");
        b2.setId(b.getId());
        Calculation after = TaxEngine.calculate(p, 2026, 1, List.of(b2, c), List.of(), List.of(), null);
        TaxDiff.Result diff = TaxDiff.compare(before, after);
        assertTrue(diff.changed());
        assertEquals(List.of("CHANGED", "ADDED", "REMOVED"), diff.lines().stream().map(TaxDiff.LineChange::kind).toList());
        assertEquals(d("500"), diff.lines().getFirst().amountDelta());
        assertEquals(d("250"), diff.totals().get("accrued")[2]); // 300 → 550
        assertTrue(diff.rates().isEmpty());

        p.setActivity(ActivityType.HIGH_TECH);
        TaxDiff.Result rate = TaxDiff.compare(after, TaxEngine.calculate(p, 2026, 1, List.of(b2, c), List.of(), List.of(), null));
        assertEquals(2, rate.rates().size()); // TRADE 10% ушла, HIGH_TECH 1% появилась
        assertFalse(TaxDiff.compare(after, after).changed());
    }

    @Test
    void issuesCarryParameters() {
        Calculation c = TaxEngine.calculate(profile(TaxRegime.TURNOVER, ActivityType.LEGAL), 2026, 1,
                List.of(), List.of(), List.of(), null);
        Issue i = c.issues().stream().filter(x -> x.code().equals("REGIME_NOT_ALLOWED")).findFirst().orElseThrow();
        assertEquals(ActivityType.LEGAL, i.params().get("activity"));
    }
}
