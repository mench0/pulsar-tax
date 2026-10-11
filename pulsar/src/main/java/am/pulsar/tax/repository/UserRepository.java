package am.pulsar.tax.repository;

import am.pulsar.tax.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCalendarToken(String calendarToken);

    List<User> findByTelegramChatIdIsNotNull();
}
