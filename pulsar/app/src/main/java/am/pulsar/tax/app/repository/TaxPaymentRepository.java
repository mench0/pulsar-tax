package am.pulsar.tax.app.repository;import am.pulsar.tax.app.entity.TaxPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;

public interface TaxPaymentRepository extends JpaRepository<TaxPayment, Long>, QuerydslPredicateExecutor<TaxPayment> {

    List<TaxPayment> findByBusinessIdAndYearOrderByDateAscIdAsc(Long businessId, int year);

    List<TaxPayment> findByBusinessIdOrderByDateAscIdAsc(Long businessId);
}
