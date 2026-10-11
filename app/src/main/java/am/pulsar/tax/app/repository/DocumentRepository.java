package am.pulsar.tax.app.repository;

import am.pulsar.tax.app.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Document> findByBusinessIdAndSha256(Long businessId, String sha256);

    List<Document> findByBusinessIdOrderByCreatedAtDescIdDesc(Long businessId);
}
