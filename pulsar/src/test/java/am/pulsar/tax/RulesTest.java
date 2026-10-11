package am.pulsar.tax;

import am.pulsar.tax.accounting.Expense;
import am.pulsar.tax.accounting.Income;
import am.pulsar.tax.business.Business;
import am.pulsar.tax.export.Csv;
import am.pulsar.tax.integrations.CbaRates;
import am.pulsar.tax.invoicing.InvoiceItem;
import am.pulsar.tax.reminders.Deadlines;
import am.pulsar.tax.shared.ApiException;
import am.pulsar.tax.shared.Enums.ActivityType;
import am.pulsar.tax.shared.Enums.Currency;
import am.pulsar.tax.shared.Enums.ExpenseCategory;
import am.pulsar.tax.shared.Enums.TaxRegime;
import am.pulsar.tax.shared.Fields;
import am.pulsar.tax.tax.Summary;
import am.pulsar.tax.tax.TaxPayment;
import am.pulsar.tax.tax.TaxRules;
import am.pulsar.tax.tax.TaxRules.ThresholdStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Чистые расчёты без базы: налоговые правила, сводка, сроки, CSV, разбор чисел и курсов. */
class RulesTest {

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    static Business profile(TaxRegime regime, ActivityType activity) {
        return profile(regime, activity, null);
    }

    static Business profile(TaxRegime regime, ActivityType activity, BigDecimal customRate) {
        Business b = new Business(1L);
        b.update("ИП", "", "", "", regime, activity, customRate);
        return b;
    }

    static Income income(String date, String amount) {
        return new Income(1L, LocalDate.parse(date), d(amount), Currency.AMD, null, null, "", "", null, null);
    }

    @Test
    void microPaysNoTurnoverTax() {
        assertEquals(d("0"), TaxRules.tax(d("5000000"), TaxRules.ratePercent(profile(TaxRegime.MICRO, ActivityType.SERVICES), 2026)));
    }

    @Test
    void turnoverRates() {
        assertEquals(d("100000"), TaxRules.tax(d("1000000"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.SERVICES), 2026)));
        assertEquals(d("10000"), TaxRules.tax(d("1000000"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.HIGH_TECH), 2026)));
    }

    @Test
    void customRate() {
        assertEquals(d("16667"), TaxRules.tax(d("333333"),
                TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.CUSTOM, d("5")), 2026)));
        assertNull(TaxRules.tax(d("100"), TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.CUSTOM), 2026)));
    }

    @Test
    void generalRegimeIsNotCalculated() {
        assertNull(TaxRules.ratePercent(profile(TaxRegime.GENERAL, ActivityType.TRADE), 2026));
        assertNull(TaxRules.forYear(2026).threshold(TaxRegime.GENERAL));
        assertEquals(ThresholdStatus.NONE, TaxRules.status(d("1"), null));
    }

    @Test
    void thresholds() {
        BigDecimal limit = TaxRules.forYear(2026).microThreshold();
        assertEquals(ThresholdStatus.OK, TaxRules.status(d("19199999"), limit));
        assertEquals(ThresholdStatus.WARNING, TaxRules.status(d("19200000"), limit));
        assertEquals(ThresholdStatus.WARNING, TaxRules.status(d("24000000"), limit));
        assertEquals(ThresholdStatus.EXCEEDED, TaxRules.status(d("24000001"), limit));
        assertEquals(d("50.0"), TaxRules.percentOf(d("12000000"), limit));
    }

    @Test
    void quarterDueDates() {
        assertEquals(LocalDate.of(2026, 4, 20), Summary.quarterDue(2026, 1));
        assertEquals(LocalDate.of(2026, 10, 20), Summary.quarterDue(2026, 3));
        assertEquals(LocalDate.of(2027, 1, 20), Summary.quarterDue(2026, 4));
    }

    @Test
    void rulesByYear() {
        assertEquals(2026, TaxRules.forYear(2026).fromYear());
        assertEquals(2026, TaxRules.forYear(2030).fromYear()); // последняя редакция действует дальше
        assertEquals(2026, TaxRules.forYear(2020).fromYear()); // до первой — берём первую
    }

    @Test
    void regimesByActivity() {
        TaxRules.YearRules r = TaxRules.forYear(2026);
        assertEquals(List.of(TaxRegime.MICRO, TaxRegime.TURNOVER, TaxRegime.GENERAL), r.allowedRegimes(ActivityType.SERVICES));
        assertEquals(List.of(TaxRegime.TURNOVER, TaxRegime.GENERAL), r.allowedRegimes(ActivityType.DESIGN));
        assertEquals(List.of(TaxRegime.GENERAL), r.allowedRegimes(ActivityType.LEGAL));
        assertEquals(List.of(TaxRegime.MICRO, TaxRegime.GENERAL), r.allowedRegimes(ActivityType.AGENCY));
        assertTrue(TaxRules.regimeAllowed(profile(TaxRegime.TURNOVER, ActivityType.DESIGN), 2026));
        assertFalse(TaxRules.regimeAllowed(profile(TaxRegime.MICRO, ActivityType.DESIGN), 2026));
        assertNull(TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.LEGAL), 2026));
        assertEquals(d("10"), TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.DESIGN), 2026));
    }

    @Test
    void numberLimits() {
        for (String bad : new String[]{"1e999999999", "1000000000000", "1.00001", "abc"}) {
            ApiException e = assertThrows(ApiException.class, () -> new Fields(Map.of("x", bad)).dec("x", true), bad);
            assertEquals(400, e.status());
        }
        assertEquals(d("999999999999"), new Fields(Map.of("x", "999999999999")).dec("x", true));
        assertEquals("1000", new Fields(Map.of("x", d("1E+3"))).dec("x", true).toPlainString());
        assertEquals(d("1.5"), new Fields(Map.of("x", "1,5")).dec("x", true));
        assertEquals(d("333"), new InvoiceItem("x", d("0.333"), d("1000.5")).sum());
    }

    @Test
    void summaryWithPaymentsAndExpenses() {
        Business p = profile(TaxRegime.TURNOVER, ActivityType.SERVICES);
        List<Income> incomes = List.of(income("2026-02-01", "1000000"), income("2026-05-01", "500000"));
        List<TaxPayment> payments = List.of(new TaxPayment(1L, 2026, 1, LocalDate.of(2026, 4, 10), d("60000")));
        List<Expense> expenses = List.of(new Expense(1L, LocalDate.of(2026, 2, 3), d("2500"), ExpenseCategory.RENT, ""));
        Map<String, Object> s = Summary.build(p, incomes, payments, expenses, 2026, LocalDate.of(2026, 6, 1));
        assertEquals(d("1500000"), s.get("totalIncome"));
        assertEquals(d("150000"), s.get("totalTax"));
        assertEquals(d("60000"), s.get("totalPaid"));
        assertEquals(d("90000"), s.get("totalDue"));
        assertEquals(d("2500"), s.get("totalExpenses"));
        assertEquals(d("10"), s.get("setAsidePercent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> q1 = ((List<Map<String, Object>>) s.get("quarters")).getFirst();
        assertEquals(d("40000"), q1.get("due"));
        assertEquals(LocalDate.of(2026, 4, 20), q1.get("dueDate"));

        Map<String, Object> notAllowed = Summary.build(profile(TaxRegime.MICRO, ActivityType.DESIGN), List.of(), List.of(),
                List.of(), 2026, LocalDate.of(2026, 6, 1));
        assertEquals(false, notAllowed.get("regimeAllowed"));
        assertEquals("REGIME_NOT_ALLOWED", ((List<Map<String, Object>>) notAllowed.get("warnings")).getFirst().get("code"));
    }

    @Test
    void calendarIcs() {
        String ics = Deadlines.ics(profile(TaxRegime.TURNOVER, ActivityType.SERVICES), "hy", LocalDate.of(2026, 10, 10));
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261020"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270120")); // 4 квартал платится в январе
        assertFalse(ics.contains("DTSTART;VALUE=DATE:20260720")); // прошедшие сроки не попадают
        assertTrue(ics.contains("TRIGGER:-P3D"));
        for (String line : ics.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, "строка длиннее 75 байт: " + line);
        }
    }

    @Test
    void reminderText() {
        Business p = profile(TaxRegime.TURNOVER, ActivityType.SERVICES);
        List<Income> incomes = List.of(income("2026-08-01", "1000000"));
        String text = Deadlines.reminder(p, "ru", LocalDate.of(2026, 10, 13),
                y -> Summary.build(p, incomes, List.of(), List.of(), y, LocalDate.of(2026, 10, 13)));
        assertTrue(text.contains("через 7 дн."), text);
        assertTrue(text.contains("3 квартал 2026"), text);
        assertTrue(text.contains("К уплате: 100000 ֏"), text);
        assertNull(Deadlines.reminder(p, "ru", LocalDate.of(2026, 10, 12), y -> Map.of()));
    }

    @Test
    void csvEscapesFormulas() {
        Income i = new Income(1L, LocalDate.of(2026, 1, 10), d("1000.00"), Currency.AMD, null, null,
                "=HYPERLINK(\"x\")", "a;b", null, null);
        String csv = Csv.incomes(List.of(i));
        assertTrue(csv.startsWith("﻿date;"));
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        assertTrue(csv.contains("\"a;b\""));
        assertTrue(csv.contains(";1000;"), "сумма без лишних нулей");
    }

    @Test
    void ratesParse() {
        Map<String, BigDecimal> r = CbaRates.parse("<ExchangeRate><ISO>USD</ISO><Amount>1</Amount><Rate>387.5</Rate></ExchangeRate>"
                + "<ExchangeRate><ISO>JPY</ISO><Amount>100</Amount><Rate>255.1</Rate></ExchangeRate>");
        assertEquals(d("387.5"), r.get("USD"));
        assertEquals(d("2.551"), r.get("JPY"));
        assertEquals(503, assertThrows(ApiException.class, () -> CbaRates.parse("<x/>")).status());
    }
}
