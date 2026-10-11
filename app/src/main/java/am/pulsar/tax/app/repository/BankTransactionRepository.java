package am.pulsar.tax.app.repository;

import am.pulsar.tax.app.entity.BankTransaction;
import am.pulsar.tax.dto.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByIdAndBusinessId(Long id, Long businessId);

    List<BankTransaction> findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(Long businessId, LocalDate from, LocalDate to);

    List<BankTransaction> findByBusinessIdAndStatusOrderByDateDescIdDesc(Long businessId, TransactionStatus status);

    List<BankTransaction> findByBusinessIdOrderByDateDescIdDesc(Long businessId);

    List<BankTransaction> findByBusinessIdAndImportIdOrderByRowNumber(Long businessId, Long importId);

    List<BankTransaction> findByBusinessIdAndFingerprintIn(Long businessId, Collection<String> fingerprints);

    List<BankTransaction> findByBusinessIdAndIncomeIdIn(Long businessId, Collection<Long> incomeIds);

    List<BankTransaction> findByBusinessIdAndExpenseIdIn(Long businessId, Collection<Long> expenseIds);

    /** Подтверждённые типы по контрагенту — история классификаций пользователя. */
    @Query("select t from BankTransaction t where t.businessId = :businessId and t.status = 'CONFIRMED' and t.counterparty <> ''")
    List<BankTransaction> confirmedWithCounterparty(Long businessId);

    @Query("select t.legacyKey from BankTransaction t where t.businessId = :businessId and t.legacyKey is not null")
    Set<String> legacyKeys(Long businessId);
}
