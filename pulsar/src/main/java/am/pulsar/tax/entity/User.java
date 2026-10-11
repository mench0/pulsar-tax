package am.pulsar.tax.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Учётная запись. Пароль — PBKDF2-SHA256 с солью (hex), как в прежней версии: старые пароли переносятся. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    @Column(name = "password_salt")
    private String passwordSalt;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "calendar_token")
    private String calendarToken;
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;
    private String lang = "ru";
    @Column(name = "last_reminder")
    private LocalDate lastReminder;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordSalt, String passwordHash, String calendarToken) {
        this.email = email;
        this.passwordSalt = passwordSalt;
        this.passwordHash = passwordHash;
        this.calendarToken = calendarToken;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordSalt() { return passwordSalt; }
    public String getPasswordHash() { return passwordHash; }
    public String getCalendarToken() { return calendarToken; }
    public Long getTelegramChatId() { return telegramChatId; }
    public String getLang() { return lang; }
    public LocalDate getLastReminder() { return lastReminder; }

    public void setPassword(String salt, String hash) {
        this.passwordSalt = salt;
        this.passwordHash = hash;
    }

    public void setTelegramChatId(Long telegramChatId) { this.telegramChatId = telegramChatId; }
    public void setLang(String lang) { this.lang = lang; }
    public void setLastReminder(LocalDate lastReminder) { this.lastReminder = lastReminder; }
}
