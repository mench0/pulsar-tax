package am.pulsar.tax.app.repository;

import am.pulsar.tax.app.entity.TaxSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxSnapshotRepository extends JpaRepository<TaxSnapshot, Long> {

    List<TaxSnapshot> findByBusinessIdAndYearOrderByCreatedAtDescIdDesc(Long businessId, int year);

    Optional<TaxSnapshot> findByIdAndBusinessId(Long id, Long businessId);
}
