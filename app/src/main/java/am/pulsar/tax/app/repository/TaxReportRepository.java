package am.pulsar.tax.app.repository;

import am.pulsar.tax.app.entity.TaxReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxReportRepository extends JpaRepository<TaxReport, Long> {

    List<TaxReport> findByBusinessIdAndYearOrderByQuarterAscVersionDesc(Long businessId, int year);

    List<TaxReport> findByBusinessIdAndYearAndTypeOrderByVersionDesc(Long businessId, int year, String type);

    Optional<TaxReport> findByIdAndBusinessId(Long id, Long businessId);
}
