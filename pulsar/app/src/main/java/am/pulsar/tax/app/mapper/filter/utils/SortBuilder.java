package am.pulsar.tax.app.mapper.filter.utils;import am.pulsar.tax.dto.filter.SearchRequest;
import com.querydsl.core.types.Path;
import org.springframework.data.domain.Sort;

public final class SortBuilder {

    private SortBuilder() {
    }

    public static Sort.Order buildOrder(Path<?> path, SearchRequest.SortDirection direction) {
        return direction == SearchRequest.SortDirection.ASC
                ? Sort.Order.asc(path.getMetadata().getName())
                : Sort.Order.desc(path.getMetadata().getName());
    }
}
