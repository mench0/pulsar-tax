package am.pulsar.tax.app.repository;

import am.pulsar.tax.app.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    List<InvoicePayment> findByBusinessId(Long businessId);

    List<InvoicePayment> findByIncomeIdIn(Collection<Long> incomeIds);

    List<InvoicePayment> findByInvoiceId(Long invoiceId);

    Optional<InvoicePayment> findByIdAndBusinessId(Long id, Long businessId);
}
