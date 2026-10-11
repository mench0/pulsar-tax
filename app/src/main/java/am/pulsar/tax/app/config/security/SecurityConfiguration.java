package am.pulsar.tax.app.config.security;import am.pulsar.tax.app.exception.ErrorResponses;
import am.pulsar.tax.app.service.AuthService;
import am.pulsar.tax.app.service.BusinessService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Без входа доступны: регистрация, вход, выход, справочник правил, календарь по секретной ссылке и корень.
 * Защита от чужих сайтов (Host, Origin, CORS) — в {@link RequestSourceFilter}; CSRF-токены не нужны:
 * тела запросов только JSON, а чужие Origin отклоняются.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain api(HttpSecurity http, AuthService authService, BusinessService businessService) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new SessionAuthFilter(authService, businessService), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/rules")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) ->
                        ErrorResponses.write(res, 401, "Войдите в Pulsar Tax")));
        return http.build();
    }

    /** Пароли проверяет AuthService; встроенный пользователь Spring Security не нужен. */
    @Bean
    public AuthenticationManager noAuthenticationManager() {
        return a -> {
            throw new ProviderNotFoundException("Вход только через /api/auth/login");
        };
    }
}
