package am.pulsar.tax.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportProfileRepository extends JpaRepository<ImportProfile, ImportProfile.Key> {

    List<ImportProfile> findByBusinessIdOrderByUpdatedAtAsc(Long businessId);
}
