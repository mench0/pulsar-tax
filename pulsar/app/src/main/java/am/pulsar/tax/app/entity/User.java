package am.pulsar.tax.app.entity;import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/** Учётная запись. Пароль — PBKDF2-SHA256 с солью (hex). */
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String passwordSalt;

    @Column(nullable = false)
    String passwordHash;

    /** Секрет личной ссылки на календарь сроков. */
    @Column(nullable = false, unique = true)
    String calendarToken;

    Long telegramChatId;

    @Column(nullable = false)
    @Builder.Default
    String lang = "ru";

    /** День последнего напоминания в Telegram: не чаще раза в день. */
    LocalDate lastReminder;
}
