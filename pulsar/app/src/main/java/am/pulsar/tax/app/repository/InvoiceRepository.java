package am.pulsar.tax.app.repository;import am.pulsar.tax.app.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, QuerydslPredicateExecutor<Invoice> {

    boolean existsByBusinessIdAndNumberIgnoreCase(Long businessId, String number);

    @Query("select i.number from Invoice i where i.businessId = :businessId and i.number like concat(:prefix, '%')")
    List<String> numbersStartingWith(Long businessId, String prefix);
}
