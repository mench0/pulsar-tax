package am.pulsar.tax.identity;

import am.pulsar.tax.web.ErrorResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Без входа доступны: регистрация, вход, выход, справочник правил, календарь по секретной ссылке и корень.
 * Защита от чужих сайтов (Host, Origin, CORS) — в {@link am.pulsar.tax.web.RequestSourceFilter};
 * CSRF-токены не нужны: тела запросов только JSON, а чужие Origin отклоняются.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, AccountService accounts) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new SessionAuthFilter(accounts), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/rules")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) ->
                        ErrorResponses.write(res, 401, "Войдите в Pulsar Tax")));
        return http.build();
    }

    /** Пароли проверяет AccountService; встроенный пользователь Spring Security не нужен. */
    @Bean
    AuthenticationManager noAuthenticationManager() {
        return a -> {
            throw new ProviderNotFoundException("Вход только через /api/auth/login");
        };
    }
}
