package am.pulsar.tax.app.config.security;import am.pulsar.tax.app.service.AuthService;
import am.pulsar.tax.app.service.BusinessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Находит пользователя по токену сессии (cookie или Bearer) и кладёт его в контекст безопасности. */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionAuthFilter extends OncePerRequestFilter {

    AuthService authService;
    BusinessService businessService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        authService.getUserIdBySession(SessionCookies.token(req)).ifPresent(userId -> {
            CurrentUserInfo user = new CurrentUserInfo(userId, businessService.getOrCreateForUser(userId).getId(),
                    SessionCookies.bearer(req) != null);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        });
        chain.doFilter(req, res);
    }
}
