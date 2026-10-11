package am.pulsar.tax.app.service;

import am.pulsar.tax.app.entity.BankTransaction;
import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.TaxReport;
import am.pulsar.tax.app.entity.TaxSnapshot;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.EntityNotFoundByIdException;
import am.pulsar.tax.app.repository.BankTransactionRepository;
import am.pulsar.tax.app.repository.TaxReportRepository;
import am.pulsar.tax.app.service.calculation.Summary;
import am.pulsar.tax.app.service.calculation.TaxDiff;
import am.pulsar.tax.app.service.calculation.TaxEngine.Calculation;
import am.pulsar.tax.app.service.calculation.TaxEngine.Issue;
import am.pulsar.tax.app.service.calculation.TaxEngine.Line;
import am.pulsar.tax.dto.enums.TaxRegime;
import am.pulsar.tax.dto.enums.TransactionKind;
import am.pulsar.tax.dto.enums.TransactionStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Подготовка налоговой отчётности к ручной подаче.
 *
 * <p>Официального API для подачи отчётов в КГД РА, доступного стороннему приложению, Pulsar не нашёл,
 * поэтому отчёт готовится для подачи пользователем в системе электронной отчётности КГД:
 * проверки полноты, фиксация цифр снимком расчёта, выгрузка и пошаговая инструкция.
 * Статус «подан» ставит только сам пользователь — Pulsar не выдаёт его за ответ налоговой.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportService {

    public static final String TURNOVER_QUARTERLY = "TURNOVER_QUARTERLY";
    public static final String MICRO_ANNUAL = "MICRO_ANNUAL";

    TaxCalculationService calculations;
    BankTransactionRepository transactions;
    TaxReportRepository reports;
    JsonMapper json;

    /** Проверка: BLOCKER не даёт подготовить отчёт, WARNING нужно подтвердить, INFO — справка. */
    public record Check(String level, String code, Map<String, Object> params) {
    }

    /** Откуда взялся оборот: доходы из подтверждённых банковских операций и введённые вручную. */
    public record Sources(int bankIncomes, BigDecimal bankSum, int manualIncomes, BigDecimal manualSum,
                          int confirmedTransactions, int pendingTransactions) {
    }

    public record Readiness(String type, int year, Integer quarter, LocalDate periodFrom, LocalDate periodTo,
                            LocalDate deadline, boolean ready, List<Check> checks, Sources sources,
                            Calculation calculation, List<Map<String, Object>> instructions) {
    }

    @Transactional(readOnly = true)
    public Readiness readiness(Business business, int year, Integer quarter, LocalDate today) {
        String type = type(business, quarter);
        Calculation calc = calculations.calculate(business, year, type.equals(MICRO_ANNUAL) ? null : quarter);
        List<Check> checks = new ArrayList<>();

        if (business.getRegime() == TaxRegime.GENERAL) checks.add(check("BLOCKER", "GENERAL_NOT_SUPPORTED", Map.of()));
        Set<String> seen = new LinkedHashSet<>();
        for (Issue i : calc.issues()) {
            if ("GENERAL_NOT_SUPPORTED".equals(i.code())) continue;
            // Однотипные замечания по правилам сворачиваются: список правил есть в расчёте.
            if ("RULE_NOT_VERIFIED".equals(i.code())) {
                if (seen.add(i.code())) checks.add(check("WARNING", "RULES_NOT_VERIFIED", Map.of()));
                continue;
            }
            String level = switch (i.severity()) {
                case "ERROR" -> "BLOCKER";
                case "WARNING" -> "WARNING";
                default -> "INFO";
            };
            checks.add(check(level, i.code(), i.params()));
        }

        // Банковские операции периода.
        List<BankTransaction> bank = transactions.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(
                business.getId(), calc.periodFrom(), calc.periodTo());
        long pending = bank.stream().filter(t -> t.getStatus() == TransactionStatus.NEEDS_REVIEW).count();
        if (pending > 0) checks.add(check("BLOCKER", "UNREVIEWED_TRANSACTIONS", Map.of("count", pending)));
        List<BankTransaction> refunds = bank.stream()
                .filter(t -> t.getStatus() == TransactionStatus.CONFIRMED && t.getKind() == TransactionKind.REFUND_ISSUED).toList();
        if (!refunds.isEmpty()) {
            checks.add(check("WARNING", "REFUNDS_ISSUED", Map.of("count", refunds.size(), "sum", sumAmd(refunds))));
        }
        List<BankTransaction> notIncome = bank.stream()
                .filter(t -> t.getStatus() == TransactionStatus.CONFIRMED && t.incoming() && t.getKind() != TransactionKind.INCOME)
                .toList();
        if (!notIncome.isEmpty()) {
            checks.add(check("INFO", "INCOMING_NOT_INCOME", Map.of("count", notIncome.size(), "sum", sumAmd(notIncome))));
        }
        boolean anyBank = !transactions.findByBusinessIdOrderByDateDescIdDesc(business.getId()).isEmpty();
        if (!anyBank) {
            checks.add(check("INFO", "NO_BANK_DATA", Map.of()));
        } else {
            List<String> missing = new ArrayList<>();
            Set<YearMonth> covered = new LinkedHashSet<>();
            for (BankTransaction t : bank) covered.add(YearMonth.from(t.getDate()));
            LocalDate end = calc.periodTo().isAfter(today) ? today : calc.periodTo();
            for (YearMonth m = YearMonth.from(calc.periodFrom()); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
                if (!covered.contains(m)) missing.add(m.toString());
            }
            if (!missing.isEmpty()) checks.add(check("WARNING", "MONTHS_WITHOUT_BANK_DATA", Map.of("months", missing)));
        }
        if (business.getTin() == null || business.getTin().isBlank()) checks.add(check("WARNING", "TIN_MISSING", Map.of()));
        if (!calc.periodTo().isBefore(today)) checks.add(check("WARNING", "PERIOD_NOT_ENDED", Map.of("periodTo", calc.periodTo())));

        LocalDate deadline = type.equals(TURNOVER_QUARTERLY) ? Summary.quarterDue(year, quarter) : null;
        List<TaxReport> previous = reports.findByBusinessIdAndYearAndTypeOrderByVersionDesc(business.getId(), year, type).stream()
                .filter(r -> java.util.Objects.equals(r.getQuarter(), type.equals(MICRO_ANNUAL) ? null : quarter)).toList();
        boolean filed = previous.stream().anyMatch(r -> TaxReport.FILED_MANUALLY.equals(r.getStatus()));
        if (deadline != null && deadline.isBefore(today) && !filed) {
            checks.add(check("WARNING", "DEADLINE_PASSED", Map.of("deadline", deadline)));
        }
        if (filed) checks.add(check("INFO", "ALREADY_FILED", Map.of()));

        // Источники оборота: сходятся ли записи учёта с подтверждёнными банковскими операциями.
        Set<Long> fromBank = new java.util.HashSet<>();
        for (BankTransaction t : bank) if (t.getIncomeId() != null) fromBank.add(t.getIncomeId());
        int bankCount = 0, manualCount = 0;
        BigDecimal bankSum = BigDecimal.ZERO, manualSum = BigDecimal.ZERO;
        for (Line l : calc.lines()) {
            if (fromBank.contains(l.incomeId())) {
                bankCount++;
                bankSum = bankSum.add(l.amountAmd());
            } else {
                manualCount++;
                manualSum = manualSum.add(l.amountAmd());
            }
        }
        Sources sources = new Sources(bankCount, bankSum, manualCount, manualSum,
                (int) bank.stream().filter(t -> t.getStatus() == TransactionStatus.CONFIRMED).count(), (int) pending);

        boolean ready = checks.stream().noneMatch(c -> c.level().equals("BLOCKER"));
        return new Readiness(type, year, type.equals(MICRO_ANNUAL) ? null : quarter, calc.periodFrom(), calc.periodTo(),
                deadline, ready, checks, sources, calc, instructions(type, calc, deadline));
    }

    /**
     * Готовит версию отчёта: фиксирует цифры снимком. Блокирующие проблемы не позволяют это сделать;
     * предупреждения нужно явно принять (acceptWarnings).
     */
    @Transactional
    public TaxReport prepare(Business business, int year, Integer quarter, boolean acceptWarnings, LocalDate today) {
        Readiness r = readiness(business, year, quarter, today);
        if (!r.ready()) {
            throw new CustomValidationException("Отчёт нельзя подготовить: " + String.join(", ",
                    r.checks().stream().filter(c -> c.level().equals("BLOCKER")).map(Check::code).toList()));
        }
        List<Check> warnings = r.checks().stream().filter(c -> c.level().equals("WARNING")).toList();
        if (!warnings.isEmpty() && !acceptWarnings) {
            throw new CustomValidationException("Подтвердите предупреждения: " + String.join(", ",
                    warnings.stream().map(Check::code).toList()));
        }
        TaxSnapshot snap = calculations.snapshot(business, year, r.quarter(), "Отчёт " + r.type());
        List<TaxReport> previous = reports.findByBusinessIdAndYearAndTypeOrderByVersionDesc(business.getId(), year, r.type()).stream()
                .filter(p -> java.util.Objects.equals(p.getQuarter(), r.quarter())).toList();
        for (TaxReport p : previous) {
            if (TaxReport.PREPARED.equals(p.getStatus())) {
                p.setStatus(TaxReport.SUPERSEDED);
                reports.save(p);
            }
        }
        var t = r.calculation().totals();
        return reports.save(TaxReport.builder()
                .businessId(business.getId()).type(r.type()).year(year).quarter(r.quarter())
                .version(previous.isEmpty() ? 1 : previous.getFirst().getVersion() + 1)
                .snapshotId(snap.getId())
                .turnover(t.turnover()).accrued(t.accrued()).paid(t.paid()).due(t.due())
                .warnings(json.writeValueAsString(warnings))
                .build());
    }

    @Transactional(readOnly = true)
    public List<TaxReport> list(Business business, int year) {
        return reports.findByBusinessIdAndYearOrderByQuarterAscVersionDesc(business.getId(), year);
    }

    @Transactional(readOnly = true)
    public TaxReport get(Business business, Long id) {
        return reports.findByIdAndBusinessId(id, business.getId())
                .orElseThrow(() -> new EntityNotFoundByIdException(TaxReport.class, id));
    }

    /** Изменились ли данные после подготовки отчёта: тогда его нужно пересчитать. */
    @Transactional(readOnly = true)
    public TaxDiff.Result changesSince(Business business, TaxReport report) {
        return calculations.diff(business, report.getSnapshotId());
    }

    /** Пользователь сообщает, что подал отчёт сам. Это его отметка, а не ответ налоговой. */
    @Transactional
    public TaxReport markFiled(Business business, Long id, LocalDate filedOn, String reference, String note, LocalDate today) {
        TaxReport r = get(business, id);
        if (TaxReport.SUPERSEDED.equals(r.getStatus())) throw new CustomValidationException("Эта версия отчёта заменена более новой");
        if (filedOn == null) throw new CustomValidationException("Укажите дату подачи");
        if (filedOn.isAfter(today)) throw new CustomValidationException("Дата подачи не может быть в будущем");
        r.setStatus(TaxReport.FILED_MANUALLY);
        r.setFiledOn(filedOn);
        r.setFilingReference(reference == null ? "" : reference.strip());
        r.setFilingNote(note == null ? "" : note.strip());
        return reports.save(r);
    }

    @Transactional
    public TaxReport unmarkFiled(Business business, Long id) {
        TaxReport r = get(business, id);
        if (!TaxReport.FILED_MANUALLY.equals(r.getStatus())) throw new CustomValidationException("Отчёт не отмечен как поданный");
        r.setStatus(TaxReport.PREPARED);
        r.setFiledOn(null);
        r.setFilingReference("");
        r.setFilingNote("");
        return reports.save(r);
    }

    /** Рабочая таблица для переноса в форму: итоги и операции, вошедшие в оборот. */
    @Transactional(readOnly = true)
    public String csv(Business business, TaxReport report) {
        Calculation c = calculations.read(calculations.snapshot(business, report.getSnapshotId()));
        StringBuilder sb = new StringBuilder("﻿");
        row(sb, "report", report.getType(), "version", report.getVersion());
        row(sb, "taxpayer", business.getName(), "tin", business.getTin());
        row(sb, "period", c.periodFrom(), c.periodTo(), "regime", c.regime());
        row(sb, "turnover_amd", c.totals().turnover());
        row(sb, "tax_base_amd", c.totals().taxBase());
        row(sb, "expenses_amd_not_deducted", c.totals().expenses());
        row(sb, "tax_unrounded_amd", c.totals().taxUnrounded());
        row(sb, "tax_accrued_amd", c.totals().accrued());
        row(sb, "tax_paid_amd", c.totals().paid());
        row(sb, "tax_due_amd", c.totals().due());
        sb.append("\r\n");
        row(sb, "rule", "rate_percent", "base_amd", "tax_unrounded_amd", "operations");
        for (var s : c.segments()) row(sb, s.ruleCode(), s.ratePercent(), s.base(), s.taxUnrounded(), s.operations());
        sb.append("\r\n");
        row(sb, "income_id", "date", "client", "description", "currency", "original_amount", "fx_rate", "amount_amd", "rate_percent", "in_base");
        for (Line l : c.lines()) {
            row(sb, l.incomeId(), l.date(), l.client(), l.description(), l.currency(), l.originalAmount(), l.fxRate(),
                    l.amountAmd(), l.ratePercent(), l.inBase());
        }
        return sb.toString();
    }

    private static List<Map<String, Object>> instructions(String type, Calculation c, LocalDate deadline) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("CHECK_DATA", Map.of()));
        steps.add(step("OPEN_SRC_SYSTEM", Map.of()));
        if (type.equals(TURNOVER_QUARTERLY)) {
            steps.add(step("FILL_TURNOVER_FORM", Map.of("quarter", c.quarter(), "year", c.year(), "turnover", c.totals().turnover())));
            steps.add(step("COMPARE_PREFILLED", Map.of()));
            steps.add(step("SIGN_AND_SUBMIT", Map.of("deadline", deadline)));
            if (c.totals().due() != null && c.totals().due().signum() > 0) {
                steps.add(step("PAY_TAX", Map.of("amount", c.totals().due(), "deadline", deadline)));
            }
        } else {
            steps.add(step("MICRO_REPORT_UNCONFIRMED", Map.of("year", c.year(), "turnover", c.totals().turnover())));
        }
        steps.add(step("MARK_FILED_IN_PULSAR", Map.of()));
        steps.add(step("SUPPORT", Map.of("phone", "08000 1008", "source", "hartak.am")));
        return steps;
    }

    private static String type(Business business, Integer quarter) {
        if (business.getRegime() == TaxRegime.MICRO) return MICRO_ANNUAL;
        if (quarter == null || quarter < 1 || quarter > 4) throw new CustomValidationException("Укажите квартал отчёта (1–4)");
        return TURNOVER_QUARTERLY;
    }

    private static BigDecimal sumAmd(List<BankTransaction> list) {
        return list.stream().map(t -> t.getAmountAmd() == null ? BigDecimal.ZERO : t.getAmountAmd().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Check check(String level, String code, Map<String, Object> params) {
        return new Check(level, code, params);
    }

    private static Map<String, Object> step(String code, Map<String, Object> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("params", params);
        return m;
    }

    private static void row(StringBuilder sb, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(';');
            Object v = cells[i];
            String s = v == null ? "" : v instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(v);
            if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof BigDecimal)) s = "'" + s;
            if (s.contains(";") || s.contains("\"") || s.contains("\n")) s = "\"" + s.replace("\"", "\"\"") + "\"";
            sb.append(s);
        }
        sb.append("\r\n");
    }
}
