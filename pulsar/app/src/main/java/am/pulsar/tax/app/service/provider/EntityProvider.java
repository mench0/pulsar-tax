package am.pulsar.tax.app.service.provider;import am.pulsar.tax.app.entity.BaseEntity;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.dto.create.BaseCreateDto;
import am.pulsar.tax.dto.patch.BasePatchDto;
import lombok.SneakyThrows;
import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Создание и изменение сущности из DTO (реализация — MapStruct). Ошибки проверки, найденные при маппинге
 * (addValidationError), выбрасываются одним CustomValidationException после маппинга.
 */
public abstract class EntityProvider<E extends BaseEntity, C extends BaseCreateDto<?>, P extends BasePatchDto<?>> {

    final ThreadLocal<CustomValidationException> validationException = new ThreadLocal<>();

    @SneakyThrows
    public final E create(C createDto) {
        validationException.set(null);
        E entity = getEntityClass().getDeclaredConstructor().newInstance();
        initializeEntity(createDto, entity);
        throwIfInvalid();
        return entity;
    }

    protected abstract void initializeEntity(C createDto, @MappingTarget E entity);

    public final void patch(P patchDto, E entity) {
        validationException.set(null);
        patchEntity(patchDto, entity);
        throwIfInvalid();
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    protected abstract void patchEntity(P patchDto, @MappingTarget E entity);

    protected abstract Class<E> getEntityClass();

    protected void addValidationError(String message) {
        if (validationException.get() == null) {
            validationException.set(new CustomValidationException());
        }
        validationException.get().addValidationError(message);
    }

    /** Строка без пробелов по краям; null — пустая строка (колонки в базе not null). */
    protected static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void throwIfInvalid() {
        CustomValidationException e = validationException.get();
        validationException.remove();
        if (e != null) {
            throw e;
        }
    }
}
