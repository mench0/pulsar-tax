package am.pulsar.tax.dto.auth;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Регистрация. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequestDto {

    String email;

    String password;

    /** Язык интерфейса: ru, en или hy. */
    String lang;

    /** true — вернуть токен в ответе (мобильное приложение) вместо cookie. */
    boolean tokenAuth;
}
