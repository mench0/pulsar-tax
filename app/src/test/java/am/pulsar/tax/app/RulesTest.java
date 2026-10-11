package am.pulsar.tax.app;import am.pulsar.tax.app.config.JacksonConfiguration;
import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.InvoiceItem;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.exception.ApiException;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.integration.cba.service.CbaRatesService;
import am.pulsar.tax.app.service.calculation.Deadlines;
import am.pulsar.tax.app.service.calculation.Summary;
import am.pulsar.tax.app.service.calculation.TaxRules.ThresholdStatus;
import am.pulsar.tax.app.service.calculation.TaxRules;
import am.pulsar.tax.app.utils.CsvWriter;
import am.pulsar.tax.dto.create.IncomeCreateDto;
import am.pulsar.tax.dto.enums.ActivityType;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import am.pulsar.tax.dto.enums.TaxRegime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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
        return Business.builder().id(1L).ownerId(1L).name("ИП").regime(regime).activity(activity).customRate(customRate).build();
    }

    static Income income(String date, String amount) {
        return Income.builder().businessId(1L).date(LocalDate.parse(date)).amount(d(amount)).build();
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
        assertEquals(2025, TaxRules.forYear(2026).fromYear());
        assertEquals(2025, TaxRules.forYear(2030).fromYear()); // последняя редакция действует дальше
        assertNull(TaxRules.forYear(2024)); // правила не переносятся на годы до первой известной редакции
        assertNull(TaxRules.ratePercent(profile(TaxRegime.TURNOVER, ActivityType.TRADE), 2024));
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

    /** Числа из JSON: строкой или числом, с запятой, с ограничением размера (JacksonConfiguration). */
    @Test
    void numberLimits() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfiguration().pulsarJsonFormats().customize(builder);
        JsonMapper json = builder.build();
        java.util.function.Function<String, BigDecimal> amount = raw ->
                json.readValue("{\"amount\":" + raw + "}", IncomeCreateDto.class).getAmount();
        for (String bad : new String[]{"\"1e999999999\"", "1000000000000", "\"1.00001\"", "\"abc\""}) {
            Exception e = assertThrows(Exception.class, () -> amount.apply(bad), bad);
            assertTrue(hasCause(e, CustomValidationException.class), bad);
        }
        assertEquals(d("999999999999"), amount.apply("\"999999999999\""));
        assertEquals("1000", amount.apply("1E+3").toPlainString());
        assertEquals(d("1.5"), amount.apply("\"1,5\""));
        assertNull(amount.apply("\"\""));
        assertEquals(d("333"), new InvoiceItem("x", d("0.333"), d("1000.5")).sum());
    }

    private static boolean hasCause(Throwable t, Class<?> type) {
        for (; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void summaryWithPaymentsAndExpenses() {
        Business p = profile(TaxRegime.TURNOVER, ActivityType.SERVICES);
        List<Income> incomes = List.of(income("2026-02-01", "1000000"), income("2026-05-01", "500000"));
        List<TaxPayment> payments = List.of(TaxPayment.builder().businessId(1L).year(2026).quarter(1).date(LocalDate.of(2026, 4, 10)).amount(d("60000")).build());
        List<Expense> expenses = List.of(Expense.builder().businessId(1L).date(LocalDate.of(2026, 2, 3)).amount(d("2500")).category(ExpenseCategory.RENT).build());
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
        Income i = Income.builder().businessId(1L).date(LocalDate.of(2026, 1, 10)).amount(d("1000.00"))
                .client("=HYPERLINK(\"x\")").description("a;b").build();
        String csv = CsvWriter.incomes(List.of(i));
        assertTrue(csv.startsWith("﻿date;"));
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        assertTrue(csv.contains("\"a;b\""));
        assertTrue(csv.contains(";1000;"), "сумма без лишних нулей");
    }

    @Test
    void ratesParse() {
        Map<String, BigDecimal> r = CbaRatesService.parse("<ExchangeRate><ISO>USD</ISO><Amount>1</Amount><Rate>387.5</Rate></ExchangeRate>"
                + "<ExchangeRate><ISO>JPY</ISO><Amount>100</Amount><Rate>255.1</Rate></ExchangeRate>");
        assertEquals(d("387.5"), r.get("USD"));
        assertEquals(d("2.551"), r.get("JPY"));
        assertEquals(503, assertThrows(ApiException.class, () -> CbaRatesService.parse("<x/>")).getStatus());
    }
}
