package am.pulsar.tax.app.mapper.filter;import am.pulsar.tax.app.mapper.filter.utils.PredicateBuilder;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.enums.sort.TaxPaymentSort;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.filter.TaxPaymentFilterDto;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static am.pulsar.tax.app.entity.QTaxPayment.taxPayment;
import static am.pulsar.tax.app.mapper.filter.utils.SortBuilder.buildOrder;

@Component
public class TaxPaymentSearchMapper extends SearchMapper<TaxPaymentFilterDto, TaxPaymentSort> {

    @Override
    protected Predicate toPredicateExceptSubFilters(TaxPaymentFilterDto filter) {
        return PredicateBuilder.builder()
                .in(taxPayment.id, filter.getIds())
                .in(taxPayment.year, filter.getYears())
                .in(taxPayment.quarter, filter.getQuarters())
                .build();
    }

    @Override
    public Predicate getAccessControlPredicate() {
        return taxPayment.businessId.eq(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Override
    protected Sort.Order toOrder(TaxPaymentSort sortName, SearchRequest.SortDirection direction) {
        return switch (sortName) {
            case DATE -> buildOrder(taxPayment.date, direction);
            case CREATED_AT -> buildOrder(taxPayment.createdAt, direction);
            case ID -> buildOrder(taxPayment.id, direction);
        };
    }

    @Override
    protected Class<TaxPaymentFilterDto> getFilterDtoClass() {
        return TaxPaymentFilterDto.class;
    }
}
