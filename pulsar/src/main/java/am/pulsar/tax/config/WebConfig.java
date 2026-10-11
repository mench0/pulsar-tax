package am.pulsar.tax.config;

import am.pulsar.tax.security.RequestSourceFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class WebConfig {

    /** Проверка источника запроса — раньше Spring Security, чтобы ответ на OPTIONS не требовал входа. */
    @Bean
    FilterRegistrationBean<RequestSourceFilter> requestSourceFilter(PulsarProperties props,
                                                                    @Value("${server.address:}") String address) {
        FilterRegistrationBean<RequestSourceFilter> bean = new FilterRegistrationBean<>(new RequestSourceFilter(props, address));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /** Деньги и курсы — числом без лишних нулей и без экспоненты: 1500, 1500.5, 402.15. */
    @Bean
    JsonMapperBuilderCustomizer plainNumbers() {
        SimpleModule module = new SimpleModule("pulsar-numbers");
        module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctx) {
                BigDecimal v = value.stripTrailingZeros();
                gen.writeNumber((v.scale() < 0 ? v.setScale(0) : v).toPlainString());
            }
        });
        return builder -> builder.addModule(module);
    }
}
