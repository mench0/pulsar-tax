package am.pulsar.tax.app.controller;import am.pulsar.tax.app.config.security.CurrentUserInfo;
import am.pulsar.tax.app.config.security.SessionCookies;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.ExternalServiceUnavailableException;
import am.pulsar.tax.app.integration.telegram.service.TelegramBot;
import am.pulsar.tax.app.mapper.entity.UserMapper;
import am.pulsar.tax.app.service.AuthService;
import am.pulsar.tax.app.service.UserService;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.TelegramLinkDto;
import am.pulsar.tax.dto.UserDto;
import am.pulsar.tax.dto.auth.AuthResponseDto;
import am.pulsar.tax.dto.request.AccountUpdateRequestDto;
import am.pulsar.tax.dto.request.PasswordChangeRequestDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Учётная запись: язык, пароль, Telegram. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountController {

    AuthService authService;
    UserService userService;
    UserMapper userMapper;
    SessionCookies sessionCookies;
    TelegramBot telegramBot;

    @PutMapping
    public UserDto update(@RequestBody AccountUpdateRequestDto request) {
        return userMapper.toDto(userService.update(AuthenticationHolder.getCurrentUserIdOrElseThrow(), request));
    }

    /** Смена пароля: выход со всех устройств, текущему — новая сессия тем же способом, каким он вошёл. */
    @PostMapping("/password")
    public AuthResponseDto changePassword(@RequestBody PasswordChangeRequestDto request, HttpServletResponse response) {
        CurrentUserInfo user = AuthenticationHolder.getCurrentUserInfoOrElseThrow();
        AuthService.Login login = authService.changePassword(user.getUserId(), request);
        if (!user.isBearer()) {
            sessionCookies.set(response, login.token());
        }
        return userMapper.toAuthResponse(login.user(), user.isBearer() ? login.token() : null);
    }

    @PostMapping("/telegram")
    public TelegramLinkDto linkTelegram() {
        if (!telegramBot.enabled()) {
            throw new CustomValidationException("Telegram на этом сервере не настроен");
        }
        if (telegramBot.username() == null) {
            throw new ExternalServiceUnavailableException("Бот Telegram ещё запускается, попробуйте через минуту");
        }
        String code = userService.telegramCode(AuthenticationHolder.getCurrentUserIdOrElseThrow());
        return new TelegramLinkDto(code, "https://t.me/" + telegramBot.username() + "?start=" + code);
    }

    @DeleteMapping("/telegram")
    public UserDto unlinkTelegram() {
        return userMapper.toDto(userService.unlinkTelegram(AuthenticationHolder.getCurrentUserIdOrElseThrow()));
    }
}
