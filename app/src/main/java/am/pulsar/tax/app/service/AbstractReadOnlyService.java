package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.BaseEntity;
import am.pulsar.tax.app.exception.EntityNotFoundByIdException;
import am.pulsar.tax.app.mapper.filter.SearchMapper;
import am.pulsar.tax.dto.filter.BaseFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import com.querydsl.core.types.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toSet;

@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract class AbstractReadOnlyService<E extends BaseEntity, F extends BaseFilterDto<?, F>> {

    Class<E> entityClass;
    QuerydslPredicateExecutor<E> querydslPredicateExecutor;
    SearchMapper<F, ?> searchMapper;

    public Optional<E> getById(@NotNull Long id) {
        return querydslPredicateExecutor.findOne(searchMapper.toPredicate(Set.of(id)));
    }

    public E getByIdOrElseThrow(@NotNull Long id) {
        return getById(id).orElseThrow(() -> new EntityNotFoundByIdException(entityClass, id));
    }

    public List<E> getByIds(@NotEmpty Set<Long> ids) {
        return fetchList(searchMapper.toPredicate(ids), Sort.unsorted());
    }

    public List<E> getByIdsOrElseThrow(@NotEmpty Set<Long> ids) {
        List<E> entities = getByIds(ids);
        if (entities.size() < ids.size()) {
            Set<Long> foundIds = entities.stream().map(BaseEntity::getId).collect(toSet());
            Set<Long> notFoundIds = ids.stream().filter(id -> !foundIds.contains(id)).collect(toSet());
            throw new EntityNotFoundByIdException(entityClass, notFoundIds);
        }
        return entities;
    }

    public List<E> search(@Valid F filter) {
        return search(SearchRequest.of(filter)).getContent();
    }

    public Page<E> search(@Valid SearchRequest<F, ?> searchRequest) {
        Predicate predicate = searchMapper.toPredicate(searchRequest.getFilter());
        Sort sort = searchMapper.toSort(searchRequest.getSorts());
        Pageable pageable = searchMapper.toPageable(searchRequest.getPagination(), sort);
        if (pageable != null) {
            return querydslPredicateExecutor.findAll(predicate, pageable);
        }
        return new PageImpl<>(fetchList(predicate, sort != null ? sort : Sort.unsorted()));
    }

    public long count(@Valid F filterDto) {
        return querydslPredicateExecutor.count(searchMapper.toPredicate(filterDto));
    }

    protected List<E> fetchList(Predicate predicate, Sort sort) {
        return StreamSupport.stream(querydslPredicateExecutor.findAll(predicate, sort).spliterator(), false).toList();
    }
}
