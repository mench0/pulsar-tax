package am.pulsar.tax.dto.enums.sort;import am.pulsar.tax.dto.IncomeDto;
import am.pulsar.tax.dto.filter.SearchRequest.Sort;
import am.pulsar.tax.dto.filter.SearchRequest.SortDirection;

import java.util.List;

public enum IncomeSort implements AvailableSorts<IncomeDto, IncomeSort> {
    DATE, CREATED_AT, ID;

    @Override
    public List<Sort<IncomeSort>> getDefaultSorts() {
        return List.of(Sort.of(DATE, SortDirection.DESC), Sort.of(ID, SortDirection.DESC));
    }
}
