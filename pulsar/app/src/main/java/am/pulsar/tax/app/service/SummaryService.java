package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.Business;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.TaxPaymentRepository;
import am.pulsar.tax.app.service.calculation.Summary;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * Годовая сводка ИП. Записи берутся по id ИП напрямую из репозиториев: сводку строит и рассылка напоминаний,
 * у которой нет вошедшего пользователя.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SummaryService {

    IncomeRepository incomeRepository;
    ExpenseRepository expenseRepository;
    TaxPaymentRepository taxPaymentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> summary(Business business, int year, LocalDate today) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return Summary.build(business,
                incomeRepository.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(business.getId(), from, to),
                taxPaymentRepository.findByBusinessIdAndYearOrderByDateAscIdAsc(business.getId(), year),
                expenseRepository.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(business.getId(), from, to),
                year, today);
    }
}
