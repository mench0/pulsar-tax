package am.pulsar.tax.app.repository;import am.pulsar.tax.app.entity.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface IncomeRepository extends JpaRepository<Income, Long>, QuerydslPredicateExecutor<Income> {

    List<Income> findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(Long businessId, LocalDate from, LocalDate to);

    List<Income> findByBusinessIdOrderByDateAscIdAsc(Long businessId);

    @Query("select i.importKey from Income i where i.businessId = :businessId and i.importKey is not null")
    Set<String> importKeys(Long businessId);
}
