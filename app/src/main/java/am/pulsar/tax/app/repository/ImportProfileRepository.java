package am.pulsar.tax.app.repository;import am.pulsar.tax.app.entity.ImportProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportProfileRepository extends JpaRepository<ImportProfile, ImportProfile.Key> {

    List<ImportProfile> findByBusinessIdOrderByUpdatedAtAsc(Long businessId);
}
