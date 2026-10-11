package am.pulsar.tax.app.mapper.filter;import am.pulsar.tax.app.mapper.filter.utils.PredicateBuilder;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.enums.sort.ExpenseSort;
import am.pulsar.tax.dto.filter.ExpenseFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static am.pulsar.tax.app.entity.QExpense.expense;
import static am.pulsar.tax.app.mapper.filter.utils.SortBuilder.buildOrder;

@Component
public class ExpenseSearchMapper extends SearchMapper<ExpenseFilterDto, ExpenseSort> {

    @Override
    protected Predicate toPredicateExceptSubFilters(ExpenseFilterDto filter) {
        return PredicateBuilder.builder()
                .in(expense.id, filter.getIds())
                .range(expense.date, yearStart(filter.getYear()), yearEnd(filter.getYear()))
                .range(expense.date, filter.getDateFrom(), filter.getDateTo())
                .in(expense.category, filter.getCategories())
                .build();
    }

    @Override
    public Predicate getAccessControlPredicate() {
        return expense.businessId.eq(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Override
    protected Sort.Order toOrder(ExpenseSort sortName, SearchRequest.SortDirection direction) {
        return switch (sortName) {
            case DATE -> buildOrder(expense.date, direction);
            case CREATED_AT -> buildOrder(expense.createdAt, direction);
            case ID -> buildOrder(expense.id, direction);
        };
    }

    @Override
    protected Class<ExpenseFilterDto> getFilterDtoClass() {
        return ExpenseFilterDto.class;
    }

    private static LocalDate yearStart(Integer year) {
        return year == null ? null : LocalDate.of(year, 1, 1);
    }

    private static LocalDate yearEnd(Integer year) {
        return year == null ? null : LocalDate.of(year, 12, 31);
    }
}
