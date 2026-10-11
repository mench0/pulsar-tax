package am.pulsar.tax.app.controller;import am.pulsar.tax.app.config.security.SessionCookies;
import am.pulsar.tax.app.mapper.entity.UserMapper;
import am.pulsar.tax.app.service.AuthService;
import am.pulsar.tax.app.service.UserService;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.UserDto;
import am.pulsar.tax.dto.auth.AuthResponseDto;
import am.pulsar.tax.dto.auth.LoginRequestDto;
import am.pulsar.tax.dto.auth.RegisterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вход и регистрация. Сессию браузер получает в cookie HttpOnly; мобильное приложение передаёт
 * "tokenAuth": true, получает token в ответе и шлёт его в Authorization: Bearer.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;
    UserService userService;
    UserMapper userMapper;
    SessionCookies sessionCookies;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@RequestBody RegisterRequestDto request, HttpServletResponse response) {
        AuthService.Login login = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(issue(response, request.isTokenAuth(), login));
    }

    @PostMapping("/login")
    public AuthResponseDto login(@RequestBody LoginRequestDto request, HttpServletResponse response) {
        return issue(response, request.isTokenAuth(), authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(SessionCookies.token(request));
        sessionCookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me() {
        return userMapper.toDto(userService.getByIdOrElseThrow(AuthenticationHolder.getCurrentUserIdOrElseThrow()));
    }

    /** Выдаёт сессию: браузеру — в cookie, приложению — токеном в ответе. */
    AuthResponseDto issue(HttpServletResponse response, boolean tokenAuth, AuthService.Login login) {
        if (!tokenAuth) {
            sessionCookies.set(response, login.token());
        }
        return userMapper.toAuthResponse(login.user(), tokenAuth ? login.token() : null);
    }
}
