package am.pulsar.tax.security;

import am.pulsar.tax.service.AccountService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Находит пользователя по токену сессии (cookie или Bearer) и кладёт его в контекст безопасности. */
public class SessionAuthFilter extends OncePerRequestFilter {

    private final AccountService accounts;

    public SessionAuthFilter(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = SessionCookies.token(req);
        accounts.userBySession(token).ifPresent(id -> {
            AuthUser user = new AuthUser(id, SessionCookies.bearer(req) != null);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        });
        chain.doFilter(req, res);
    }
}
