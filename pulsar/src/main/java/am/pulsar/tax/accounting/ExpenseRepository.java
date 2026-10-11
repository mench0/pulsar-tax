package am.pulsar.tax.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(Long businessId, LocalDate from, LocalDate to);

    List<Expense> findByBusinessIdOrderByDateAscIdAsc(Long businessId);

    Optional<Expense> findByIdAndBusinessId(Long id, Long businessId);
}
