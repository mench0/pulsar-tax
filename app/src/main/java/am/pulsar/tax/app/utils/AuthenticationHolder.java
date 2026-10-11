package am.pulsar.tax.app.utils;import am.pulsar.tax.app.config.security.CurrentUserInfo;
import am.pulsar.tax.app.exception.AuthenticationFailedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Текущий пользователь из контекста Spring Security. */
public final class AuthenticationHolder {

    private AuthenticationHolder() {
    }

    public static Optional<CurrentUserInfo> getCurrentUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo info)) {
            return Optional.empty();
        }
        return Optional.of(info);
    }

    public static CurrentUserInfo getCurrentUserInfoOrElseThrow() {
        return getCurrentUserInfo().orElseThrow(() -> new AuthenticationFailedException("Войдите в Pulsar Tax"));
    }

    public static Long getCurrentUserIdOrElseThrow() {
        return getCurrentUserInfoOrElseThrow().getUserId();
    }

    public static Long getCurrentBusinessIdOrElseThrow() {
        return getCurrentUserInfoOrElseThrow().getBusinessId();
    }
}
