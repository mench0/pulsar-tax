package am.pulsar.tax.dto.auth;import am.pulsar.tax.dto.UserDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/** Ответ на вход и регистрацию. Токен — только приложению, которое просило tokenAuth; браузеру — cookie. */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponseDto extends UserDto {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String token;
}
