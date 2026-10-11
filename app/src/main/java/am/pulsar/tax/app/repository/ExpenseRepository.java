package am.pulsar.tax.app.repository;import am.pulsar.tax.app.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, QuerydslPredicateExecutor<Expense> {

    List<Expense> findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(Long businessId, LocalDate from, LocalDate to);

    List<Expense> findByBusinessIdOrderByDateAscIdAsc(Long businessId);
}
