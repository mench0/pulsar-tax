package am.pulsar.tax.app.mapper.entity;import am.pulsar.tax.app.entity.BaseEntity;
import am.pulsar.tax.dto.BaseDto;
import am.pulsar.tax.dto.filter.SearchResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface EntityMapper<E extends BaseEntity, D extends BaseDto> {

    D toDto(E entity);

    default List<D> toDtoList(List<E> entities) {
        return entities.stream().map(this::toDto).toList();
    }

    default SearchResponse<D> toSearchResponse(Page<E> entities) {
        SearchResponse<D> searchResponse = new SearchResponse<>();
        searchResponse.setContent(toDtoList(entities.getContent()));
        if (entities.getPageable().isPaged()) {
            searchResponse.setPageSize(entities.getPageable().getPageSize());
            searchResponse.setPageNumber(entities.getPageable().getPageNumber());
            searchResponse.setTotalElements(entities.getTotalElements());
            searchResponse.setTotalPages(entities.getTotalPages());
        }
        return searchResponse;
    }
}
