package am.pulsar.tax.app.mapper.filter.utils;import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringPath;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PredicateBuilder {

    BooleanBuilder builder;

    public static PredicateBuilder builder() {
        return new PredicateBuilder(new BooleanBuilder(Expressions.asBoolean(true).isTrue()));
    }

    public static PredicateBuilder builder(Predicate predicate) {
        return new PredicateBuilder(new BooleanBuilder(predicate));
    }

    public static Predicate buildAlwaysFalsePredicate() {
        return new BooleanBuilder(Expressions.asBoolean(true).isFalse());
    }

    public Predicate build() {
        return builder.getValue();
    }

    public <T> PredicateBuilder eq(SimpleExpression<T> attribute, T value) {
        return eq(attribute, value, Objects::nonNull);
    }

    public <T> PredicateBuilder eq(SimpleExpression<T> attribute, T value, Function<T, Boolean> validator) {
        if (validator.apply(value)) {
            builder.and(attribute.eq(value));
        }
        return this;
    }

    public <T> PredicateBuilder in(SimpleExpression<T> attribute, Collection<T> value) {
        if (!CollectionUtils.isEmpty(value)) {
            builder.and(attribute.in(value));
        }
        return this;
    }

    /** Строка из набора без учёта регистра. */
    public PredicateBuilder inIgnoreCase(StringPath attribute, Collection<String> value) {
        if (!CollectionUtils.isEmpty(value)) {
            builder.and(attribute.lower().in(value.stream().map(String::toLowerCase).toList()));
        }
        return this;
    }

    /** Диапазон значений; пустая граница не ограничивает. */
    public <T extends Comparable<?>> PredicateBuilder range(ComparableExpression<T> attribute, T start, T end) {
        if (start != null && end != null) {
            builder.and(attribute.between(start, end));
        } else if (start != null) {
            builder.and(attribute.goe(start));
        } else if (end != null) {
            builder.and(attribute.loe(end));
        }
        return this;
    }

    public PredicateBuilder containsIgnoreCase(StringPath attribute, String value) {
        if (Objects.nonNull(value) && !value.isBlank()) {
            builder.and(attribute.containsIgnoreCase(value));
        }
        return this;
    }

    public PredicateBuilder isTrue(BooleanExpression booleanExpression) {
        builder.and(booleanExpression);
        return this;
    }

    public PredicateBuilder and(Predicate predicate) {
        builder.and(predicate);
        return this;
    }

    public PredicateBuilder or(Predicate predicate) {
        builder.or(predicate);
        return this;
    }
}
