package am.pulsar.tax.app.mapper.filter;import am.pulsar.tax.app.mapper.filter.utils.PredicateBuilder;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.enums.sort.IncomeSort;
import am.pulsar.tax.dto.filter.IncomeFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static am.pulsar.tax.app.entity.QIncome.income;
import static am.pulsar.tax.app.mapper.filter.utils.SortBuilder.buildOrder;

@Component
public class IncomeSearchMapper extends SearchMapper<IncomeFilterDto, IncomeSort> {

    @Override
    protected Predicate toPredicateExceptSubFilters(IncomeFilterDto filter) {
        return PredicateBuilder.builder()
                .in(income.id, filter.getIds())
                .range(income.date, yearStart(filter.getYear()), yearEnd(filter.getYear()))
                .range(income.date, filter.getDateFrom(), filter.getDateTo())
                .in(income.currency, filter.getCurrencies())
                .in(income.invoiceId, filter.getInvoiceIds())
                .in(income.importKey, filter.getImportKeys())
                .containsIgnoreCase(income.client, filter.getClient())
                .build();
    }

    @Override
    public Predicate getAccessControlPredicate() {
        return income.businessId.eq(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Override
    protected Sort.Order toOrder(IncomeSort sortName, SearchRequest.SortDirection direction) {
        return switch (sortName) {
            case DATE -> buildOrder(income.date, direction);
            case CREATED_AT -> buildOrder(income.createdAt, direction);
            case ID -> buildOrder(income.id, direction);
        };
    }

    @Override
    protected Class<IncomeFilterDto> getFilterDtoClass() {
        return IncomeFilterDto.class;
    }

    private static LocalDate yearStart(Integer year) {
        return year == null ? null : LocalDate.of(year, 1, 1);
    }

    private static LocalDate yearEnd(Integer year) {
        return year == null ? null : LocalDate.of(year, 12, 31);
    }
}
