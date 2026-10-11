package am.pulsar.tax.app.mapper.filter;import am.pulsar.tax.app.mapper.filter.utils.PredicateBuilder;
import am.pulsar.tax.dto.enums.sort.AvailableSorts;
import am.pulsar.tax.dto.filter.BaseFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import com.querydsl.core.types.Predicate;
import lombok.SneakyThrows;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Фильтр, сортировки и пагинация из запроса поиска → предикат QueryDSL и Pageable. */
public abstract class SearchMapper<F extends BaseFilterDto<?, F>, S extends AvailableSorts<?, S>> {

    @SneakyThrows
    public Predicate toPredicate(Set<Long> ids) {
        F filter = getFilterDtoClass().getDeclaredConstructor().newInstance();
        filter.setIds(ids);
        return toPredicate(filter);
    }

    public Predicate toPredicate(F filter) {
        Predicate accessControlPredicate = getAccessControlPredicate();
        if (accessControlPredicate == null) {
            accessControlPredicate = PredicateBuilder.builder().build();
        }
        Predicate predicate = toPredicateRecursive(filter);
        return PredicateBuilder.builder(predicate).and(accessControlPredicate).build();
    }

    protected Predicate toPredicateRecursive(F filter) {
        if (filter == null) {
            return PredicateBuilder.builder().build();
        }
        Predicate predicate = toPredicateExceptSubFilters(filter);
        if (predicate == null) {
            predicate = PredicateBuilder.builder().build();
        }
        if (CollectionUtils.isEmpty(filter.getSubFiltersAggregatedByOr())) {
            return predicate;
        }
        Predicate subPredicate = filter.getSubFiltersAggregatedByOr().stream()
                .map(this::toPredicateRecursive)
                .reduce((p1, p2) -> PredicateBuilder.builder(p1).or(p2).build())
                .orElse(null);
        return subPredicate != null ? PredicateBuilder.builder(predicate).and(subPredicate).build() : predicate;
    }

    protected abstract Predicate toPredicateExceptSubFilters(F filter);

    /** Ограничение доступа: пользователь видит только записи своего ИП. */
    public abstract Predicate getAccessControlPredicate();

    @SuppressWarnings("unchecked")
    public Sort toSort(List<? extends SearchRequest.Sort<?>> sorts) {
        if (CollectionUtils.isEmpty(sorts)) {
            return null;
        }
        List<Sort.Order> orders = new ArrayList<>();
        sorts.forEach(sort -> {
            Sort.Order order = toOrder((S) sort.getSortName(), sort.getDirection());
            if (order == null) {
                throw new UnsupportedOperationException("Sort " + sort.getSortName() + " is not supported");
            }
            orders.add(order);
        });
        return orders.isEmpty() ? null : Sort.by(orders);
    }

    public Pageable toPageable(SearchRequest.Pagination pagination, Sort sort) {
        if (pagination == null) {
            return null;
        }
        return sort != null
                ? PageRequest.of(pagination.getPageNumber(), pagination.getPageSize(), sort)
                : PageRequest.of(pagination.getPageNumber(), pagination.getPageSize());
    }

    protected abstract Sort.Order toOrder(S sortName, SearchRequest.SortDirection direction);

    protected abstract Class<F> getFilterDtoClass();
}
