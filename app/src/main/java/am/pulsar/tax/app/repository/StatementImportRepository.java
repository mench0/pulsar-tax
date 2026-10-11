package am.pulsar.tax.app.repository;

import am.pulsar.tax.app.entity.StatementImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {

    List<StatementImport> findByBusinessIdOrderByCreatedAtDescIdDesc(Long businessId);

    Optional<StatementImport> findFirstByBusinessIdAndFileSha256OrderByIdDesc(Long businessId, String fileSha256);

    Optional<StatementImport> findByIdAndBusinessId(Long id, Long businessId);
}
