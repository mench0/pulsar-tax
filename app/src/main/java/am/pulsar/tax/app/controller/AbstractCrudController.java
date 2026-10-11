package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.BaseEntity;
import am.pulsar.tax.app.mapper.entity.EntityMapper;
import am.pulsar.tax.app.service.AbstractCrudService;
import am.pulsar.tax.dto.BaseDto;
import am.pulsar.tax.dto.create.BaseCreateDto;
import am.pulsar.tax.dto.enums.sort.AvailableSorts;
import am.pulsar.tax.dto.filter.BaseFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.filter.SearchResponse;
import am.pulsar.tax.dto.patch.BasePatchDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Общие операции над сущностями ИП: получение по id, поиск с фильтром, сортировками и пагинацией,
 * подсчёт, создание, частичное изменение и удаление. Доступ ограничен записями своего ИП (SearchMapper).
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract class AbstractCrudController<E extends BaseEntity, D extends BaseDto, C extends BaseCreateDto<D>, P extends BasePatchDto<D>,
        F extends BaseFilterDto<D, F>, S extends AvailableSorts<D, S>> {

    AbstractCrudService<E, C, P, F> service;
    EntityMapper<E, D> mapper;

    /** Поиск по id. */
    @GetMapping("/{id}")
    public D getById(@PathVariable("id") Long id) {
        return mapper.toDto(service.getByIdOrElseThrow(id));
    }

    /** Поиск по фильтру с пагинацией и сортировкой. */
    @PostMapping("/search")
    public SearchResponse<D> search(@RequestBody SearchRequest<F, S> searchRequest) {
        return mapper.toSearchResponse(service.search(searchRequest));
    }

    /** Количество записей по фильтру. */
    @PostMapping("/count")
    public long count(@RequestBody F filterDto) {
        return service.count(filterDto);
    }

    /** Создание. */
    @PostMapping
    public ResponseEntity<D> create(@RequestBody C createDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(service.create(createDto)));
    }

    /** Изменение отдельных полей. */
    @PatchMapping
    public D patch(@RequestBody P patchDto) {
        return mapper.toDto(service.patch(patchDto));
    }

    /** Удаление по id. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
