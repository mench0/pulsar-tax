package am.pulsar.tax.repository;

import am.pulsar.tax.entity.Business;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findByOwnerId(Long ownerId);

    /** Создаёт профиль, если его ещё нет; параллельные первые запросы не мешают друг другу. */
    @Modifying
    @Query(value = "insert into businesses (owner_id) values (:ownerId) on conflict (owner_id) do nothing", nativeQuery = true)
    void insertIfAbsent(Long ownerId);
}
