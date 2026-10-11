package am.pulsar.tax.dto.filter;import am.pulsar.tax.dto.enums.sort.AvailableSorts;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchRequest<F extends BaseFilterDto<?, F>, S extends AvailableSorts<?, S>> {

    /** Фильтр. */
    F filter;

    /** Пагинация; без неё возвращается всё. */
    Pagination pagination;

    /** Сортировки. */
    List<Sort<S>> sorts;

    public static <F extends BaseFilterDto<?, F>, S extends AvailableSorts<?, S>> SearchRequest<F, S> of(F filter, List<Sort<S>> sorts) {
        return new SearchRequest<>(filter, null, sorts);
    }

    public static <F extends BaseFilterDto<?, F>> SearchRequest<F, ?> of(F filterDto) {
        SearchRequest<F, ?> searchRequest = new SearchRequest<>();
        searchRequest.setFilter(filterDto);
        return searchRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Pagination {

        /** Номер страницы (начинается с 0). */
        int pageNumber;

        /** Размер страницы. */
        @Builder.Default
        int pageSize = 20;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Sort<S extends AvailableSorts<?, S>> {

        /** Название сортировки. */
        S sortName;

        /** Направление сортировки. */
        SortDirection direction = SortDirection.ASC;

        public static <S extends AvailableSorts<?, S>> Sort<S> of(S sort, SortDirection sortDirection) {
            return new Sort<>(sort, sortDirection);
        }
    }

    public enum SortDirection {
        ASC, DESC
    }
}
