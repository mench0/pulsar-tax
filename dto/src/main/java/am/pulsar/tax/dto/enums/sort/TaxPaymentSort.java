package am.pulsar.tax.dto.enums.sort;import am.pulsar.tax.dto.TaxPaymentDto;
import am.pulsar.tax.dto.filter.SearchRequest.Sort;
import am.pulsar.tax.dto.filter.SearchRequest.SortDirection;

import java.util.List;

public enum TaxPaymentSort implements AvailableSorts<TaxPaymentDto, TaxPaymentSort> {
    DATE, CREATED_AT, ID;

    @Override
    public List<Sort<TaxPaymentSort>> getDefaultSorts() {
        return List.of(Sort.of(DATE, SortDirection.DESC), Sort.of(ID, SortDirection.DESC));
    }
}
