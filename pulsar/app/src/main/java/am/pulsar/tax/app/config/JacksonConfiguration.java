package am.pulsar.tax.app.config;import am.pulsar.tax.app.exception.CustomValidationException;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Формат чисел и дат в JSON.
 *
 * Деньги и курсы пишутся числом без лишних нулей и экспоненты (1500, 1500.5, 402.15). На входе числа
 * принимаются и числом, и строкой из поля ввода («150000,50»), пустая строка — «не указано».
 * Размер ограничен: «1e999999999» иначе проходит разбор и превращается в строку из миллиарда цифр.
 */
@Configuration
public class JacksonConfiguration {

    /** Целых цифр не больше: 999 млрд — с запасом выше любых порогов. */
    public static final int MAX_INT_DIGITS = 12;
    public static final int MAX_SCALE = 4;

    @Bean
    public JsonMapperBuilderCustomizer pulsarJsonFormats() {
        SimpleModule module = new SimpleModule("pulsar-formats");
        module.addSerializer(BigDecimal.class, new PlainDecimalSerializer());
        module.addDeserializer(BigDecimal.class, new LenientDecimalDeserializer());
        module.addDeserializer(LocalDate.class, new LenientDateDeserializer());
        // Пропущенный в JSON флаг (например tokenAuth) — false, а не ошибка.
        return builder -> builder.addModule(module).disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    static class PlainDecimalSerializer extends ValueSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctx) {
            BigDecimal v = value.stripTrailingZeros();
            gen.writeNumber((v.scale() < 0 ? v.setScale(0) : v).toPlainString());
        }
    }

    static class LenientDecimalDeserializer extends ValueDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
            String field = p.currentName();
            BigDecimal d;
            JsonToken token = p.currentToken();
            if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                d = p.getDecimalValue();
            } else if (token == JsonToken.VALUE_STRING) {
                String s = p.getString().trim();
                if (s.isEmpty()) {
                    return null;
                }
                try {
                    d = new BigDecimal(s.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new CustomValidationException("Поле «" + field + "» должно быть числом");
                }
            } else {
                throw new CustomValidationException("Поле «" + field + "» должно быть числом");
            }
            return limited(field, d);
        }

        static BigDecimal limited(String field, BigDecimal d) {
            BigDecimal n = d.stripTrailingZeros();
            if (n.precision() - n.scale() > MAX_INT_DIGITS) {
                throw new CustomValidationException("Поле «" + field + "» слишком большое");
            }
            if (n.scale() > MAX_SCALE) {
                throw new CustomValidationException("Поле «" + field + "»: не больше " + MAX_SCALE + " знаков после запятой");
            }
            return n.scale() < 0 ? n.setScale(0) : n;
        }
    }

    static class LenientDateDeserializer extends ValueDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
            String field = p.currentName();
            String s = p.currentToken() == JsonToken.VALUE_STRING ? p.getString().trim() : null;
            if (s != null && s.isEmpty()) {
                return null;
            }
            try {
                if (s == null) {
                    throw new DateTimeParseException("не строка", "", 0);
                }
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                throw new CustomValidationException("Дата «" + field + "» должна быть в формате ГГГГ-ММ-ДД");
            }
        }
    }
}
