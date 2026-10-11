package am.pulsar.tax.dto.auth;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Вход по почте и паролю. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequestDto {

    String email;

    String password;

    /** true — вернуть токен в ответе (мобильное приложение) вместо cookie. */
    boolean tokenAuth;
}
