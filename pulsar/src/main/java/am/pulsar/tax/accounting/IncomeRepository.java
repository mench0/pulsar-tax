package am.pulsar.tax.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(Long businessId, LocalDate from, LocalDate to);

    List<Income> findByBusinessIdOrderByDateAscIdAsc(Long businessId);

    Optional<Income> findByIdAndBusinessId(Long id, Long businessId);

    @Query("select i.importKey from Income i where i.businessId = :businessId and i.importKey is not null")
    Set<String> importKeys(Long businessId);
}
