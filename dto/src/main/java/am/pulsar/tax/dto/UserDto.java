package am.pulsar.tax.dto;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/** Текущий пользователь. */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDto extends BaseDto {

    String email;

    /** Язык: ru, en или hy. */
    String lang;

    /** Настроен ли на сервере бот Telegram. */
    Boolean telegramEnabled;

    /** Привязан ли Telegram пользователя. */
    Boolean telegramLinked;

    /** Ссылка на личный календарь сроков (.ics). */
    String calendarPath;
}
