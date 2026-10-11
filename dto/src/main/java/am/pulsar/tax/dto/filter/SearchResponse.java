package am.pulsar.tax.dto.filter;import lombok.AccessLevel;
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
public class SearchResponse<T> {

    /** Найденные сущности (с учётом фильтра, сортировки и пагинации). */
    List<T> content;

    /** Номер страницы (начинается с 0). */
    Integer pageNumber;

    /** Размер страницы. */
    Integer pageSize;

    /** Всего сущностей по фильтру. */
    Long totalElements;

    /** Всего страниц. */
    Integer totalPages;
}
