package am.pulsar.tax.app.config;import am.pulsar.tax.app.config.security.RequestSourceFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class WebConfiguration {

    /** Проверка источника запроса — раньше Spring Security, чтобы ответ на OPTIONS не требовал входа. */
    @Bean
    public FilterRegistrationBean<RequestSourceFilter> requestSourceFilter(PulsarProperties props,
                                                                           @Value("${server.address:}") String address) {
        FilterRegistrationBean<RequestSourceFilter> bean = new FilterRegistrationBean<>(new RequestSourceFilter(props, address));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
