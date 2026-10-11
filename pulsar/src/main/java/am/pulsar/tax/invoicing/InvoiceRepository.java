package am.pulsar.tax.invoicing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByBusinessIdOrderByDateDescIdDesc(Long businessId);

    Optional<Invoice> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByBusinessIdAndNumberIgnoreCase(Long businessId, String number);

    @Query("select i.number from Invoice i where i.businessId = :businessId and i.number like concat(:prefix, '%')")
    List<String> numbersStartingWith(Long businessId, String prefix);
}
