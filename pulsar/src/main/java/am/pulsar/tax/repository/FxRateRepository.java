package am.pulsar.tax.repository;

import am.pulsar.tax.entity.FxRate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface FxRateRepository extends JpaRepository<FxRate, FxRate.Key> {

    List<FxRate> findByDateOrderByCurrency(LocalDate date);

    @Modifying
    @Query("delete from FxRate r where r.date = :date")
    void deleteByDate(LocalDate date);
}
