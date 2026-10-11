package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.BaseEntity;
import am.pulsar.tax.app.exception.NotAvailableOperationException;
import am.pulsar.tax.app.mapper.filter.SearchMapper;
import am.pulsar.tax.app.service.provider.EntityProvider;
import am.pulsar.tax.dto.create.BaseCreateDto;
import am.pulsar.tax.dto.filter.BaseFilterDto;
import am.pulsar.tax.dto.patch.BasePatchDto;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Validated
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class AbstractCrudService<E extends BaseEntity, C extends BaseCreateDto<?>, P extends BasePatchDto<?>, F extends BaseFilterDto<?, F>>
        extends AbstractReadOnlyService<E, F> {

    final EntityProvider<E, C, P> entityProvider;
    final JpaRepository<E, Long> repository;

    protected AbstractCrudService(Class<E> entityClass,
                                  EntityProvider<E, C, P> entityProvider,
                                  JpaRepository<E, Long> jpaRepository,
                                  QuerydslPredicateExecutor<E> querydslPredicateExecutor,
                                  SearchMapper<F, ?> searchMapper) {
        super(entityClass, querydslPredicateExecutor, searchMapper);
        this.entityProvider = entityProvider;
        this.repository = jpaRepository;
    }

    @Transactional
    public E create(@Valid C createDto) {
        assertWriteAccess();
        E entity = entityProvider.create(createDto);
        processEntityBeforeSaving(createDto, entity);
        return repository.saveAndFlush(entity);
    }

    protected void processEntityBeforeSaving(C createDto, E entity) {
    }

    @Transactional
    public E patch(@Valid P patchDto) {
        E entity = getByIdOrElseThrow(patchDto.getId());
        assertWriteAccess(patchDto.getId());
        entityProvider.patch(patchDto, entity);
        processEntityBeforeSaving(patchDto, entity);
        return repository.saveAndFlush(entity);
    }

    protected void processEntityBeforeSaving(P patchDto, E entity) {
    }

    @Transactional
    public void delete(Long id) {
        delete(Set.of(id));
    }

    @Transactional
    public void delete(Set<Long> ids) {
        List<E> entities = getByIdsOrElseThrow(ids);
        assertWriteAccess(ids);
        processEntitiesBeforeDeleting(entities);
        repository.deleteAll(entities);
    }

    protected void processEntitiesBeforeDeleting(List<E> entities) {
    }

    protected final void assertWriteAccess() {
        if (!hasWriteAccess()) {
            throw new NotAvailableOperationException("Нет доступа");
        }
    }

    protected final void assertWriteAccess(Long id) {
        if (!hasWriteAccess(id)) {
            throw new NotAvailableOperationException("Нет доступа");
        }
    }

    protected final void assertWriteAccess(Collection<Long> ids) {
        if (!ids.stream().allMatch(this::hasWriteAccess)) {
            throw new NotAvailableOperationException("Нет доступа");
        }
    }

    protected abstract boolean hasWriteAccess();

    protected boolean hasWriteAccess(Long id) {
        return hasWriteAccess();
    }
}
