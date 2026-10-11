package am.pulsar.tax.app.service;

import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.entity.FxRate;
import am.pulsar.tax.app.entity.TaxSnapshot;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.EntityNotFoundByIdException;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.repository.FxRateRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.TaxPaymentRepository;
import am.pulsar.tax.app.repository.TaxSnapshotRepository;
import am.pulsar.tax.app.service.calculation.TaxDiff;
import am.pulsar.tax.app.service.calculation.TaxEngine;
import am.pulsar.tax.app.service.calculation.TaxEngine.Calculation;
import am.pulsar.tax.dto.enums.Currency;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Подробный налоговый расчёт ИП за квартал или год и снимки расчёта для сравнения.
 * Курсы для проверки операций в валюте берутся только из кэша ЦБ РА: расчёт не ходит в сеть.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxCalculationService {

    IncomeRepository incomeRepository;
    ExpenseRepository expenseRepository;
    TaxPaymentRepository taxPaymentRepository;
    FxRateRepository fxRateRepository;
    TaxSnapshotRepository snapshotRepository;
    JsonMapper json;

    @Transactional(readOnly = true)
    public Calculation calculate(Business business, int year, Integer quarter) {
        if (quarter != null && (quarter < 1 || quarter > 4)) throw new CustomValidationException("Квартал — от 1 до 4");
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        Map<String, BigDecimal> cache = new LinkedHashMap<>();
        return TaxEngine.calculate(business, year, quarter,
                incomeRepository.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(business.getId(), from, to),
                expenseRepository.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(business.getId(), from, to),
                taxPaymentRepository.findByBusinessIdAndYearOrderByDateAscIdAsc(business.getId(), year),
                (date, currency) -> cbaRate(cache, date, currency));
    }

    private BigDecimal cbaRate(Map<String, BigDecimal> cache, LocalDate date, Currency currency) {
        String key = date + currency.name();
        if (!cache.containsKey(key)) {
            cache.put(key, fxRateRepository.findById(new FxRate.Key(date, currency.name())).map(FxRate::getRate).orElse(null));
        }
        return cache.get(key);
    }

    /** Сохраняет текущий расчёт: потом можно увидеть, что и почему в нём изменилось. */
    @Transactional
    public TaxSnapshot snapshot(Business business, int year, Integer quarter, String reason) {
        Calculation c = calculate(business, year, quarter);
        return snapshotRepository.save(TaxSnapshot.builder()
                .businessId(business.getId()).year(year).quarter(quarter)
                .reason(reason == null ? "" : reason.strip())
                .accrued(c.totals().accrued())
                .payload(json.writeValueAsString(c))
                .build());
    }

    @Transactional(readOnly = true)
    public List<TaxSnapshot> snapshots(Business business, int year) {
        return snapshotRepository.findByBusinessIdAndYearOrderByCreatedAtDescIdDesc(business.getId(), year);
    }

    @Transactional(readOnly = true)
    public TaxSnapshot snapshot(Business business, Long id) {
        return snapshotRepository.findByIdAndBusinessId(id, business.getId())
                .orElseThrow(() -> new EntityNotFoundByIdException(TaxSnapshot.class, id));
    }

    public Calculation read(TaxSnapshot s) {
        return json.readValue(s.getPayload(), Calculation.class);
    }

    /** Что изменилось с момента снимка: операции, ставки и итоги. */
    @Transactional(readOnly = true)
    public TaxDiff.Result diff(Business business, Long snapshotId) {
        TaxSnapshot s = snapshot(business, snapshotId);
        return TaxDiff.compare(read(s), calculate(business, s.getYear(), s.getQuarter()));
    }
}
