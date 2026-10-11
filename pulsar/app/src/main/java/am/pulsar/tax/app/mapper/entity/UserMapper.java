package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.config.PulsarProperties;
import am.pulsar.tax.app.entity.User;
import am.pulsar.tax.dto.UserDto;
import am.pulsar.tax.dto.auth.AuthResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserMapper implements EntityMapper<User, UserDto> {

    @Autowired
    protected PulsarProperties props;

    @Override
    @Mapping(target = "telegramEnabled", expression = "java(props.telegramEnabled())")
    @Mapping(target = "telegramLinked", expression = "java(entity.getTelegramChatId() != null)")
    @Mapping(target = "calendarPath", expression = "java(calendarPath(entity))")
    public abstract UserDto toDto(User entity);

    /** Ответ на вход: токен — только приложению, которое просило tokenAuth (иначе null, и поля нет в JSON). */
    @Mapping(target = "telegramEnabled", expression = "java(props.telegramEnabled())")
    @Mapping(target = "telegramLinked", expression = "java(entity.getTelegramChatId() != null)")
    @Mapping(target = "calendarPath", expression = "java(calendarPath(entity))")
    public abstract AuthResponseDto toAuthResponse(User entity, String token);

    protected String calendarPath(User user) {
        return "/cal/" + user.getCalendarToken() + ".ics";
    }
}
