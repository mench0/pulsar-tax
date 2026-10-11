package am.pulsar.tax.dto.enums.sort;import am.pulsar.tax.dto.ExpenseDto;
import am.pulsar.tax.dto.filter.SearchRequest.Sort;
import am.pulsar.tax.dto.filter.SearchRequest.SortDirection;

import java.util.List;

public enum ExpenseSort implements AvailableSorts<ExpenseDto, ExpenseSort> {
    DATE, CREATED_AT, ID;

    @Override
    public List<Sort<ExpenseSort>> getDefaultSorts() {
        return List.of(Sort.of(DATE, SortDirection.DESC), Sort.of(ID, SortDirection.DESC));
    }
}
