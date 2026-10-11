package am.pulsar.tax.app.repository;import am.pulsar.tax.app.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface SessionRepository extends JpaRepository<Session, String> {

    @Modifying
    @Query("delete from Session s where s.userId = :userId")
    void deleteByUser(Long userId);

    @Modifying
    @Query("delete from Session s where s.expiresAt < :now")
    int deleteExpired(Instant now);
}
