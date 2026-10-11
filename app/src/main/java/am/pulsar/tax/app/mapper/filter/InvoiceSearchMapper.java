package am.pulsar.tax.app.mapper.filter;import am.pulsar.tax.app.mapper.filter.utils.PredicateBuilder;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.enums.sort.InvoiceSort;
import am.pulsar.tax.dto.filter.InvoiceFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static am.pulsar.tax.app.entity.QInvoice.invoice;
import static am.pulsar.tax.app.mapper.filter.utils.SortBuilder.buildOrder;

@Component
public class InvoiceSearchMapper extends SearchMapper<InvoiceFilterDto, InvoiceSort> {

    @Override
    protected Predicate toPredicateExceptSubFilters(InvoiceFilterDto filter) {
        return PredicateBuilder.builder()
                .in(invoice.id, filter.getIds())
                .range(invoice.date, yearStart(filter.getYear()), yearEnd(filter.getYear()))
                .in(invoice.status, filter.getStatuses())
                .inIgnoreCase(invoice.number, filter.getNumbers())
                .containsIgnoreCase(invoice.client, filter.getClient())
                .build();
    }

    @Override
    public Predicate getAccessControlPredicate() {
        return invoice.businessId.eq(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Override
    protected Sort.Order toOrder(InvoiceSort sortName, SearchRequest.SortDirection direction) {
        return switch (sortName) {
            case DATE -> buildOrder(invoice.date, direction);
            case NUMBER -> buildOrder(invoice.number, direction);
            case CREATED_AT -> buildOrder(invoice.createdAt, direction);
            case ID -> buildOrder(invoice.id, direction);
        };
    }

    @Override
    protected Class<InvoiceFilterDto> getFilterDtoClass() {
        return InvoiceFilterDto.class;
    }

    private static LocalDate yearStart(Integer year) {
        return year == null ? null : LocalDate.of(year, 1, 1);
    }

    private static LocalDate yearEnd(Integer year) {
        return year == null ? null : LocalDate.of(year, 12, 31);
    }
}
