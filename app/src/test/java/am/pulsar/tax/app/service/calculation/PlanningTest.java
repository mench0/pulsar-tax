package am.pulsar.tax.app.service.calculation;

import am.pulsar.tax.app.service.calculation.Planning.Forecast;
import am.pulsar.tax.app.service.calculation.Planning.MonthFlow;
import am.pulsar.tax.app.service.calculation.Planning.Receivable;
import am.pulsar.tax.app.service.calculation.Planning.TaxDue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Прогноз денег: история, известные платежи, оценка налога, диапазон и предупреждения. */
class PlanningTest {

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    static final LocalDate TODAY = LocalDate.of(2026, 10, 1); // первый день месяца — текущий месяц целиком

    @Test
    void projectionSeparatesKnownAndEstimatedAndShowsRange() {
        List<MonthFlow> history = List.of(
                new MonthFlow(YearMonth.of(2026, 7), d("100000"), d("40000")),
                new MonthFlow(YearMonth.of(2026, 8), d("200000"), d("60000")),
                new MonthFlow(YearMonth.of(2026, 9), d("300000"), d("50000")));
        Forecast f = Planning.forecast(TODAY, 4, history,
                List.of(new Receivable(1L, "PT-1", d("50000"), LocalDate.of(2026, 11, 15), false)),
                List.of(new TaxDue("2026-Q3", LocalDate.of(2026, 10, 20), d("60000"), true)),
                d("10"), d("500000"), TODAY, Summary::quarterDue);
        assertEquals(d("200000"), f.avgInflow());
        assertEquals(d("50000"), f.avgOutflow());
        Planning.Month oct = f.projection().get(0);
        assertEquals(d("60000"), oct.taxes()); // налог за 3 квартал — известный, в месяц срока
        assertTrue(oct.taxItems().getFirst().known());
        assertEquals(d("590000"), oct.closing()); // 500 000 + 200 000 − 50 000 − 60 000
        assertEquals(d("480000"), oct.closingLow()); // 500 000 + мин. 100 000 − макс. 60 000 − налог 60 000
        assertEquals(d("700000"), oct.closingHigh()); // 500 000 + макс. 300 000 − мин. 40 000 − 60 000
        Planning.Month nov = f.projection().get(1);
        assertEquals(d("50000"), nov.receivables());
        // Налог 4 квартала по прогнозу (октябрь–декабрь: 200 000 × 3 + счёт 50 000) × 10% — оценка в январе.
        Planning.Month jan = f.projection().get(3);
        assertEquals(d("65000"), jan.taxes());
        assertFalse(jan.taxItems().getFirst().known());
        assertTrue(f.assumptions().contains("ESTIMATED_TAX_AT_CURRENT_RATE"));
        assertTrue(f.warnings().isEmpty());
    }

    @Test
    void warnsAboutShortfallBeforeTaxDeadlineAndMissingData() {
        Forecast f = Planning.forecast(TODAY, 2, List.of(), List.of(),
                List.of(new TaxDue("2026-Q3", LocalDate.of(2026, 10, 20), d("60000"), true),
                        new TaxDue("2026-Q2", LocalDate.of(2026, 7, 20), d("1000"), true)),
                d("10"), null, null, Summary::quarterDue);
        List<String> codes = f.warnings().stream().map(Planning.Warning::code).toList();
        assertTrue(codes.contains("NO_HISTORY"));
        assertTrue(codes.contains("NO_STARTING_CASH"));
        assertTrue(codes.contains("TAX_OVERDUE")); // срок за 2 квартал прошёл — платёж в первом месяце
        assertTrue(codes.contains("POSSIBLE_SHORTFALL_FOR_TAX"));
        assertEquals(d("61000"), f.projection().getFirst().taxes());
    }

    @Test
    void overdueReceivablesAreNotCountedInTheBadCase() {
        Forecast f = Planning.forecast(TODAY, 1, List.of(),
                List.of(new Receivable(1L, "PT-1", d("80000"), LocalDate.of(2026, 9, 1), true)),
                List.of(), null, d("0"), TODAY, Summary::quarterDue);
        assertEquals(d("80000"), f.projection().getFirst().closing());
        assertEquals(d("0"), f.projection().getFirst().closingLow());
    }
}
