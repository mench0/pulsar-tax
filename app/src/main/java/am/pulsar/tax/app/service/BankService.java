package am.pulsar.tax.app.service;

import am.pulsar.tax.app.entity.BankTransaction;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.StatementImport;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.EntityNotFoundByIdException;
import am.pulsar.tax.app.integration.cba.service.CbaRatesService;
import am.pulsar.tax.app.repository.BankTransactionRepository;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.StatementImportRepository;
import am.pulsar.tax.app.repository.TaxPaymentRepository;
import am.pulsar.tax.app.service.bank.StatementParser;
import am.pulsar.tax.app.service.bank.TransactionClassifier;
import am.pulsar.tax.app.service.bank.TransactionClassifier.History;
import am.pulsar.tax.app.service.bank.TransactionClassifier.Suggestion;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.app.utils.ClockUtils;
import am.pulsar.tax.dto.bank.BankTransactionDto;
import am.pulsar.tax.dto.bank.StatementImportResultDto;
import am.pulsar.tax.dto.bank.StatementMappingDto;
import am.pulsar.tax.dto.bank.StatementPreviewDto;
import am.pulsar.tax.dto.bank.StatementRowDto;
import am.pulsar.tax.dto.bank.StatementUploadDto;
import am.pulsar.tax.dto.bank.TransactionReviewDto;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import am.pulsar.tax.dto.enums.TransactionKind;
import am.pulsar.tax.dto.enums.TransactionStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Банковские операции: проверка выписки до сохранения, защита от дублей, подсказка типа и подтверждение.
 *
 * <p>В налоговый учёт операция попадает только после подтверждения: подтверждённое поступление-доход
 * создаёт запись дохода, расход и комиссия — запись расхода, уплата налога — запись уплаты.
 * Перевод между своими счетами, возвраты и прочие операции на налог не влияют.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BankService {

    static final int MAX_ROWS = 5000;

    BankTransactionRepository transactions;
    StatementImportRepository imports;
    IncomeRepository incomes;
    ExpenseRepository expenses;
    TaxPaymentRepository taxPayments;
    CbaRatesService cbaRates;
    JsonMapper json;
    TransactionTemplate tx;

    /** Строка после разбора — общее для просмотра и сохранения. */
    record Parsed(StatementRowDto dto, List<String> cells, String legacyKey) {
    }

    // ---------- Просмотр и сохранение выписки ----------

    /** Без общей транзакции: курсы ЦБ запрашиваются по сети, соединение с базой на это время не держим. */
    public StatementPreviewDto preview(StatementUploadDto upload) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        List<Parsed> rows = parse(businessId, upload);
        Long sameFile = upload.fileSha256() == null || upload.fileSha256().isBlank() ? null
                : imports.findFirstByBusinessIdAndFileSha256OrderByIdDesc(businessId, upload.fileSha256())
                .map(StatementImport::getId).orElse(null);
        List<StatementRowDto> dtos = rows.stream().map(Parsed::dto).toList();
        int errors = (int) dtos.stream().filter(r -> !r.errors().isEmpty()).count();
        int duplicates = (int) dtos.stream().filter(StatementRowDto::duplicate).count();
        int review = (int) dtos.stream().filter(r -> r.errors().isEmpty() && !r.duplicate()
                && r.confidence().compareTo(TransactionClassifier.CONFIDENT) < 0).count();
        return new StatementPreviewDto(dtos.size(), dtos.size() - errors - duplicates, errors, duplicates, review, dtos, sameFile);
    }

    /**
     * Сохраняет выписку: строки с ошибками, дубли и отмеченные «пропустить» не сохраняются.
     * Строка с типом, выбранным пользователем, сразу подтверждается; остальные ждут проверки.
     */
    public StatementImportResultDto commit(StatementUploadDto upload) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        // Разбор и курсы ЦБ — вне транзакции; сохранение — одной транзакцией.
        List<Parsed> rows = parse(businessId, upload);
        return tx.execute(status -> save(businessId, upload, rows));
    }

    private StatementImportResultDto save(Long businessId, StatementUploadDto upload, List<Parsed> rows) {
        Map<Integer, StatementUploadDto.RowOverride> overrides = new HashMap<>();
        if (upload.overrides() != null) {
            for (StatementUploadDto.RowOverride o : upload.overrides()) {
                if (o != null && o.row() != null) overrides.put(o.row(), o);
            }
        }
        StatementImport imp = imports.save(StatementImport.builder()
                .businessId(businessId)
                .fileName(trim(upload.fileName(), 255))
                .fileSha256(trim(upload.fileSha256(), 64))
                .account(trim(upload.account(), 100))
                .mapping(json.writeValueAsString(upload.mapping()))
                .rowsTotal(rows.size())
                .build());
        int added = 0, duplicates = 0, errors = 0, skipped = 0, confirmed = 0;
        List<BankTransaction> fresh = new ArrayList<>();
        for (Parsed p : rows) {
            StatementRowDto r = p.dto();
            StatementUploadDto.RowOverride o = overrides.get(r.row());
            if (!r.errors().isEmpty()) {
                errors++;
                continue;
            }
            if (r.duplicate()) {
                duplicates++;
                continue;
            }
            if (o != null && Boolean.TRUE.equals(o.skip())) {
                skipped++;
                continue;
            }
            BankTransaction t = BankTransaction.builder()
                    .businessId(businessId).importId(imp.getId()).rowNumber(r.row())
                    .date(r.date()).amount(r.amount()).currency(Currency.valueOf(r.currency()))
                    .rate(r.rate()).amountAmd(r.amountAmd())
                    .counterparty(trim(r.counterparty(), 200)).description(trim(r.description(), 500))
                    .reference(trim(r.reference(), 100)).account(trim(upload.account(), 100))
                    .fingerprint(r.fingerprint()).legacyKey(p.legacyKey())
                    .suggestedKind(r.suggestedKind()).confidence(r.confidence())
                    .reasons(String.join(",", r.reasons()))
                    .raw(json.writeValueAsString(p.cells()))
                    .build();
            fresh.add(transactions.save(t));
            added++;
            if (o != null && o.kind() != null && !o.kind().isBlank()) {
                apply(t, kind(o.kind()), o.rate(), null, null, null);
                confirmed++;
            }
        }
        transactions.flush();
        // Пары «списание здесь — поступление на другом своём счёте» помечаются как вероятный перевод.
        for (BankTransaction t : fresh) {
            if (t.getStatus() == TransactionStatus.NEEDS_REVIEW) suggestOwnTransfer(t);
        }
        imp.setRowsAdded(added);
        imp.setRowsDuplicate(duplicates);
        imp.setRowsError(errors);
        imp.setRowsSkipped(skipped);
        imports.save(imp);
        return new StatementImportResultDto(imp.getId(), added, duplicates, errors, skipped, confirmed, added - confirmed);
    }

    private List<Parsed> parse(Long businessId, StatementUploadDto upload) {
        if (upload == null || upload.rows() == null || upload.mapping() == null) {
            throw new CustomValidationException("Нужны строки выписки и сопоставление колонок");
        }
        if (upload.rows().size() > MAX_ROWS) {
            throw new CustomValidationException("За раз можно загрузить не больше " + MAX_ROWS + " строк");
        }
        StatementMappingDto m = upload.mapping();
        int dateCol = m.col(m.date());
        int amountCol = m.col(m.amount());
        if (dateCol < 0 || amountCol < 0) throw new CustomValidationException("Укажите колонки даты и суммы");
        String format = m.dateFormat() == null ? "dd.mm.yyyy" : m.dateFormat();
        if (!StatementParser.DATE_FORMATS.contains(format)) throw new CustomValidationException("Неизвестный формат даты: " + format);
        Currency defaultCurrency = currency(m.currency() == null ? "AMD" : m.currency());
        if (defaultCurrency == null) throw new CustomValidationException("Неизвестная валюта: " + m.currency());

        Map<Integer, BigDecimal> rateOverrides = new HashMap<>();
        if (upload.overrides() != null) {
            for (StatementUploadDto.RowOverride o : upload.overrides()) {
                if (o != null && o.row() != null && o.rate() != null && o.rate().signum() > 0) rateOverrides.put(o.row(), o.rate());
            }
        }

        LocalDate today = ClockUtils.today();
        Set<String> knownLegacy = new HashSet<>(incomes.importKeys(businessId));
        knownLegacy.addAll(transactions.legacyKeys(businessId));
        Map<String, History> history = history(businessId);
        Map<String, BigDecimal> rateCache = new HashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        Set<String> legacyInFile = new HashSet<>();

        List<Parsed> parsed = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        for (int n = 0; n < upload.rows().size(); n++) {
            List<String> cells = upload.rows().get(n) == null ? List.of() : upload.rows().get(n);
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            LocalDate date = StatementParser.date(StatementParser.cell(cells, dateCol), format);
            if (date == null) errors.add("DATE_INVALID");
            else if (date.isAfter(today)) warnings.add("DATE_IN_FUTURE");

            BigDecimal credit = StatementParser.amount(StatementParser.cell(cells, amountCol));
            BigDecimal debit = m.col(m.debit()) >= 0 ? StatementParser.amount(StatementParser.cell(cells, m.col(m.debit()))) : null;
            BigDecimal amount = debit != null && debit.signum() != 0 && (credit == null || credit.signum() == 0)
                    ? debit.abs().negate() : credit;
            if (amount == null) errors.add("AMOUNT_INVALID");
            else if (amount.signum() == 0) errors.add("AMOUNT_ZERO");
            else if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) errors.add("AMOUNT_PRECISION");
            else if (amount.abs().compareTo(new BigDecimal("99999999999999")) > 0) errors.add("AMOUNT_TOO_LARGE");

            String rawCurrency = m.col(m.currencyCol()) >= 0 ? StatementParser.cell(cells, m.col(m.currencyCol())) : null;
            Currency cur = defaultCurrency;
            if (rawCurrency != null && !rawCurrency.isBlank()) {
                cur = currency(rawCurrency.strip().toUpperCase());
                if (cur == null) errors.add("CURRENCY_UNSUPPORTED");
            }

            String description = text(StatementParser.cell(cells, m.col(m.desc())));
            String counterparty = text(StatementParser.cell(cells, m.col(m.counterparty())));
            String reference = text(StatementParser.cell(cells, m.col(m.reference())));

            BigDecimal rate = null;
            String rateSource = null;
            BigDecimal amountAmd = null;
            if (cur == Currency.AMD) {
                amountAmd = amount;
                rateSource = "AMD";
            } else if (cur != null && date != null && amount != null) {
                rate = rateOverrides.get(n + 1);
                rateSource = rate == null ? null : "MANUAL";
                if (rate == null && !date.isAfter(today)) {
                    rate = cbaRate(rateCache, date, cur, today);
                    rateSource = rate == null ? null : "CBA";
                }
                if (rate == null) warnings.add("RATE_UNKNOWN");
                else amountAmd = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            }

            String key = String.join("\u001f", cells);
            int occurrence = occurrences.merge(key, 1, Integer::sum) - 1;
            String fingerprint = StatementParser.fingerprint(cells, occurrence);
            String legacyKey = StatementParser.legacyKey(cells);
            boolean legacyDuplicate = knownLegacy.contains(legacyKey);
            // Одинаковые строки внутри одного файла — не дубли (две одинаковые комиссии за день бывают).
            if (!legacyInFile.add(legacyKey)) legacyKey = null;

            Suggestion s = amount == null || amount.signum() == 0
                    ? new Suggestion(TransactionKind.OTHER, BigDecimal.ZERO, List.of())
                    : TransactionClassifier.classify(amount, counterparty, description,
                    history.get(StatementParser.normalize(counterparty)));

            if (s.kind() == TransactionKind.INCOME && amountAmd != null && date != null && !legacyDuplicate
                    && incomes.existsByBusinessIdAndDateAndAmountAndImportKeyIsNull(businessId, date, amountAmd.abs())) {
                warnings.add("POSSIBLE_MANUAL_DUPLICATE");
            }
            fingerprints.add(fingerprint);
            parsed.add(new Parsed(new StatementRowDto(n + 1, date, amount, cur == null ? rawCurrency : cur.name(),
                    counterparty, description, reference, rate, rateSource, amountAmd,
                    s.kind(), s.confidence(), s.reasons(), errors, warnings,
                    legacyDuplicate, legacyDuplicate ? "INCOME_IMPORT" : null, fingerprint), cells, legacyKey));
        }

        Set<String> existing = new HashSet<>();
        for (BankTransaction t : transactions.findByBusinessIdAndFingerprintIn(businessId, fingerprints)) existing.add(t.getFingerprint());
        if (existing.isEmpty()) return parsed;
        List<Parsed> marked = new ArrayList<>();
        for (Parsed p : parsed) {
            StatementRowDto r = p.dto();
            if (!r.duplicate() && existing.contains(r.fingerprint())) {
                r = new StatementRowDto(r.row(), r.date(), r.amount(), r.currency(), r.counterparty(), r.description(),
                        r.reference(), r.rate(), r.rateSource(), r.amountAmd(), r.suggestedKind(), r.confidence(),
                        r.reasons(), r.errors(), r.warnings(), true, "TRANSACTION", r.fingerprint());
            }
            marked.add(new Parsed(r, p.cells(), p.legacyKey()));
        }
        return marked;
    }

    private BigDecimal cbaRate(Map<String, BigDecimal> cache, LocalDate date, Currency cur, LocalDate today) {
        String key = date.toString();
        if (!cache.containsKey(key + cur)) {
            try {
                Map<String, BigDecimal> rates = cbaRates.on(date, today);
                for (Currency c : Currency.values()) cache.put(key + c, rates.get(c.name()));
            } catch (RuntimeException e) {
                // ЦБ недоступен: курс укажет пользователь, строка получит предупреждение RATE_UNKNOWN.
                for (Currency c : Currency.values()) cache.putIfAbsent(key + c, null);
            }
        }
        return cache.get(key + cur);
    }

    /** История решений пользователя по контрагенту: какой тип он выбирал и всегда ли один и тот же. */
    private Map<String, History> history(Long businessId) {
        Map<String, Map<TransactionKind, Integer>> counts = new HashMap<>();
        for (BankTransaction t : transactions.confirmedWithCounterparty(businessId)) {
            if (t.getKind() == null) continue;
            counts.computeIfAbsent(StatementParser.normalize(t.getCounterparty()), k -> new LinkedHashMap<>())
                    .merge(t.getKind(), 1, Integer::sum);
        }
        Map<String, History> h = new HashMap<>();
        counts.forEach((cp, byKind) -> {
            Map.Entry<TransactionKind, Integer> top = byKind.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            h.put(cp, new History(top.getKey(), top.getValue(), byKind.size() == 1));
        });
        return h;
    }

    private void suggestOwnTransfer(BankTransaction t) {
        BankTransaction pair = findTransferPair(t);
        if (pair == null) return;
        t.setSuggestedKind(TransactionKind.OWN_TRANSFER);
        t.setConfidence(new BigDecimal("0.8"));
        t.setReasons(t.getReasons() + ",MATCHES_OTHER_ACCOUNT");
        t.setLinkedTransactionId(pair.getId());
        transactions.save(t);
    }

    /** Встречная операция на другом своём счёте: та же сумма с другим знаком, разница в датах до двух дней. */
    private BankTransaction findTransferPair(BankTransaction t) {
        return transactions.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(t.getBusinessId(),
                        t.getDate().minusDays(2), t.getDate().plusDays(2)).stream()
                .filter(o -> !o.getId().equals(t.getId()))
                .filter(o -> !Objects.equals(o.getAccount(), t.getAccount()) || o.getCurrency() != t.getCurrency())
                .filter(o -> o.getCurrency() == t.getCurrency() && o.getAmount().compareTo(t.getAmount().negate()) == 0)
                .filter(o -> o.getStatus() != TransactionStatus.IGNORED
                        && (o.getKind() == null || o.getKind() == TransactionKind.OWN_TRANSFER))
                .findFirst().orElse(null);
    }

    // ---------- Проверка операций ----------

    @Transactional(readOnly = true)
    public List<BankTransactionDto> list(LocalDate from, LocalDate to, TransactionStatus status) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        List<BankTransaction> list;
        if (from != null || to != null) {
            list = transactions.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(businessId,
                    from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? LocalDate.of(2100, 12, 31) : to);
            if (status != null) list = list.stream().filter(t -> t.getStatus() == status).toList();
        } else if (status != null) {
            list = transactions.findByBusinessIdAndStatusOrderByDateDescIdDesc(businessId, status);
        } else {
            list = transactions.findByBusinessIdOrderByDateDescIdDesc(businessId);
        }
        return list.stream().map(BankService::toDto).toList();
    }

    @Transactional
    public BankTransactionDto review(Long id, TransactionReviewDto review) {
        BankTransaction t = get(id);
        if (review == null) throw new CustomValidationException("Нужно решение по операции");
        if (Boolean.TRUE.equals(review.ignore())) {
            undo(t);
            t.setKind(null);
            t.setStatus(TransactionStatus.IGNORED);
        } else if (Boolean.TRUE.equals(review.reopen())) {
            undo(t);
            t.setKind(null);
            t.setStatus(TransactionStatus.NEEDS_REVIEW);
        } else {
            ExpenseCategory category = null;
            if (review.expenseCategory() != null && !review.expenseCategory().isBlank()) {
                try {
                    category = ExpenseCategory.valueOf(review.expenseCategory());
                } catch (IllegalArgumentException e) {
                    throw new CustomValidationException("Неизвестная категория расхода: " + review.expenseCategory());
                }
            }
            apply(t, kind(review.kind()), review.rate(), review.taxYear(), review.taxQuarter(), category);
        }
        return toDto(transactions.save(t));
    }

    /** Подтверждает подсказки с высокой уверенностью — только по явной команде пользователя. */
    @Transactional
    public int confirmConfident(List<Long> ids) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        int n = 0;
        for (BankTransaction t : transactions.findByBusinessIdAndStatusOrderByDateDescIdDesc(businessId, TransactionStatus.NEEDS_REVIEW)) {
            if (ids != null && !ids.isEmpty() && !ids.contains(t.getId())) continue;
            if (t.getConfidence().compareTo(TransactionClassifier.CONFIDENT) < 0) continue;
            if (t.getSuggestedKind() == TransactionKind.OTHER || t.getSuggestedKind() == TransactionKind.TAX_PAYMENT) continue;
            if (t.getCurrency() != Currency.AMD && t.getRate() == null) continue;
            apply(t, t.getSuggestedKind(), null, null, null, null);
            transactions.save(t);
            n++;
        }
        return n;
    }

    private BankTransaction get(Long id) {
        return transactions.findByIdAndBusinessId(id, AuthenticationHolder.getCurrentBusinessIdOrElseThrow())
                .orElseThrow(() -> new EntityNotFoundByIdException(BankTransaction.class, id));
    }

    /** Подтверждает тип: снимает прежние последствия и создаёт записи учёта для нового типа. */
    void apply(BankTransaction t, TransactionKind kind, BigDecimal rate, Integer taxYear, Integer taxQuarter,
               ExpenseCategory category) {
        if (kind == null) throw new CustomValidationException("Укажите тип операции");
        if (!TransactionClassifier.signFits(kind, t.incoming())) {
            throw new CustomValidationException(t.incoming() ? "Это поступление: такой тип возможен только для списания"
                    : "Это списание: такой тип возможен только для поступления");
        }
        undo(t);
        if (rate != null) {
            if (rate.signum() <= 0) throw new CustomValidationException("Курс должен быть больше нуля");
            if (t.getCurrency() != Currency.AMD) {
                t.setRate(rate);
                t.setAmountAmd(t.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
            }
        }
        boolean needsAmd = kind == TransactionKind.INCOME || kind == TransactionKind.EXPENSE
                || kind == TransactionKind.BANK_FEE || kind == TransactionKind.TAX_PAYMENT;
        if (needsAmd && t.getAmountAmd() == null) {
            throw new CustomValidationException("Укажите курс ЦБ РА на " + t.getDate() + " для " + t.getCurrency());
        }
        BigDecimal amd = t.getAmountAmd() == null ? null : t.getAmountAmd().abs();
        switch (kind) {
            case INCOME -> {
                Income i = incomes.save(Income.builder()
                        .businessId(t.getBusinessId()).date(t.getDate()).amount(amd)
                        .currency(t.getCurrency())
                        .originalAmount(t.getCurrency() == Currency.AMD ? null : t.getAmount().abs())
                        .rate(t.getCurrency() == Currency.AMD ? null : t.getRate())
                        .client(trim(t.getCounterparty(), 200))
                        .description(trim(t.getDescription(), 500))
                        .importKey("bank-" + t.getFingerprint())
                        .build());
                t.setIncomeId(i.getId());
            }
            case EXPENSE, BANK_FEE -> {
                String desc = t.getCounterparty().isBlank() ? t.getDescription() : t.getCounterparty() + " — " + t.getDescription();
                Expense e = expenses.save(Expense.builder()
                        .businessId(t.getBusinessId()).date(t.getDate()).amount(amd)
                        .category(kind == TransactionKind.BANK_FEE ? ExpenseCategory.BANK_FEES
                                : category != null ? category : ExpenseCategory.OTHER)
                        .description(trim(desc, 500))
                        .build());
                t.setExpenseId(e.getId());
            }
            case TAX_PAYMENT -> {
                if (taxYear != null || taxQuarter != null) {
                    if (taxYear == null || taxQuarter == null || taxQuarter < 0 || taxQuarter > 4 || taxYear < 2000 || taxYear > 2100) {
                        throw new CustomValidationException("Укажите год и квартал уплаты (0 — за год)");
                    }
                    TaxPayment p = taxPayments.save(TaxPayment.builder()
                            .businessId(t.getBusinessId()).year(taxYear).quarter(taxQuarter)
                            .date(t.getDate()).amount(amd).build());
                    t.setTaxPaymentId(p.getId());
                }
            }
            case OWN_TRANSFER -> {
                BankTransaction pair = t.getLinkedTransactionId() != null
                        ? transactions.findById(t.getLinkedTransactionId()).orElse(null) : findTransferPair(t);
                if (pair != null) {
                    t.setLinkedTransactionId(pair.getId());
                    if (pair.getLinkedTransactionId() == null) {
                        pair.setLinkedTransactionId(t.getId());
                        transactions.save(pair);
                    }
                }
            }
            default -> {
                // Возвраты и прочие операции на налог не влияют автоматически.
            }
        }
        t.setKind(kind);
        t.setStatus(TransactionStatus.CONFIRMED);
    }

    /** Удаляет записи учёта, созданные этой операцией. */
    private void undo(BankTransaction t) {
        if (t.getIncomeId() != null) {
            incomes.findById(t.getIncomeId()).ifPresent(incomes::delete);
            t.setIncomeId(null);
        }
        if (t.getExpenseId() != null) {
            expenses.findById(t.getExpenseId()).ifPresent(expenses::delete);
            t.setExpenseId(null);
        }
        if (t.getTaxPaymentId() != null) {
            taxPayments.findById(t.getTaxPaymentId()).ifPresent(taxPayments::delete);
            t.setTaxPaymentId(null);
        }
        if (t.getKind() == TransactionKind.OWN_TRANSFER && t.getLinkedTransactionId() != null) {
            transactions.findById(t.getLinkedTransactionId()).ifPresent(p -> {
                if (t.getId().equals(p.getLinkedTransactionId()) && p.getKind() != TransactionKind.OWN_TRANSFER) {
                    p.setLinkedTransactionId(null);
                    transactions.save(p);
                }
            });
        }
    }

    /** Доход или расход удалён вручную: операция снова ждёт проверки, а не числится подтверждённой без записи. */
    @Transactional
    public void detachIncomes(Long businessId, List<Long> incomeIds) {
        if (incomeIds.isEmpty()) return;
        for (BankTransaction t : transactions.findByBusinessIdAndIncomeIdIn(businessId, incomeIds)) {
            t.setIncomeId(null);
            t.setKind(null);
            t.setStatus(TransactionStatus.NEEDS_REVIEW);
            transactions.save(t);
        }
    }

    @Transactional
    public void detachExpenses(Long businessId, List<Long> expenseIds) {
        if (expenseIds.isEmpty()) return;
        for (BankTransaction t : transactions.findByBusinessIdAndExpenseIdIn(businessId, expenseIds)) {
            t.setExpenseId(null);
            t.setKind(null);
            t.setStatus(TransactionStatus.NEEDS_REVIEW);
            transactions.save(t);
        }
    }

    // ---------- Выписки ----------

    @Transactional(readOnly = true)
    public List<StatementImport> imports() {
        return imports.findByBusinessIdOrderByCreatedAtDescIdDesc(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Transactional(readOnly = true)
    public List<BankTransactionDto> importRows(Long importId) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        imports.findByIdAndBusinessId(importId, businessId)
                .orElseThrow(() -> new EntityNotFoundByIdException(StatementImport.class, importId));
        return transactions.findByBusinessIdAndImportIdOrderByRowNumber(businessId, importId).stream().map(BankService::toDto).toList();
    }

    /** Повторная классификация неподтверждённых операций выписки — например, после новых решений пользователя. */
    @Transactional
    public int reclassify(Long importId) {
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        imports.findByIdAndBusinessId(importId, businessId)
                .orElseThrow(() -> new EntityNotFoundByIdException(StatementImport.class, importId));
        Map<String, History> history = history(businessId);
        int n = 0;
        for (BankTransaction t : transactions.findByBusinessIdAndImportIdOrderByRowNumber(businessId, importId)) {
            if (t.getStatus() != TransactionStatus.NEEDS_REVIEW) continue;
            Suggestion s = TransactionClassifier.classify(t.getAmount(), t.getCounterparty(), t.getDescription(),
                    history.get(StatementParser.normalize(t.getCounterparty())));
            t.setSuggestedKind(s.kind());
            t.setConfidence(s.confidence());
            t.setReasons(String.join(",", s.reasons()));
            transactions.save(t);
            suggestOwnTransfer(t);
            n++;
        }
        return n;
    }

    static BankTransactionDto toDto(BankTransaction t) {
        return new BankTransactionDto(t.getId(), t.getImportId(), t.getRowNumber(), t.getDate(), t.getAmount(),
                t.getCurrency().name(), t.getRate(), t.getAmountAmd(), t.getCounterparty(), t.getDescription(),
                t.getReference(), t.getAccount(), t.getSuggestedKind(), t.getConfidence(),
                t.getReasons().isEmpty() ? List.of() : Arrays.asList(t.getReasons().split(",")),
                t.getKind(), t.getStatus(), t.getIncomeId(), t.getExpenseId(), t.getTaxPaymentId(), t.getLinkedTransactionId());
    }

    private static TransactionKind kind(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TransactionKind.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new CustomValidationException("Неизвестный тип операции: " + raw);
        }
    }

    private static Currency currency(String raw) {
        try {
            return Currency.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.strip();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
