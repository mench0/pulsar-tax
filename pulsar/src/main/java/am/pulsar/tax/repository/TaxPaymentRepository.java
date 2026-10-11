package am.pulsar.tax.repository;

import am.pulsar.tax.entity.TaxPayment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxPaymentRepository extends JpaRepository<TaxPayment, Long> {

    List<TaxPayment> findByBusinessIdAndYearOrderByDateAscIdAsc(Long businessId, int year);

    List<TaxPayment> findByBusinessIdOrderByDateAscIdAsc(Long businessId);

    Optional<TaxPayment> findByIdAndBusinessId(Long id, Long businessId);
}
