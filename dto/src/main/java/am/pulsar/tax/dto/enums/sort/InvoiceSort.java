package am.pulsar.tax.dto.enums.sort;import am.pulsar.tax.dto.InvoiceDto;
import am.pulsar.tax.dto.filter.SearchRequest.Sort;
import am.pulsar.tax.dto.filter.SearchRequest.SortDirection;

import java.util.List;

public enum InvoiceSort implements AvailableSorts<InvoiceDto, InvoiceSort> {
    DATE, NUMBER, CREATED_AT, ID;

    @Override
    public List<Sort<InvoiceSort>> getDefaultSorts() {
        return List.of(Sort.of(DATE, SortDirection.DESC), Sort.of(ID, SortDirection.DESC));
    }
}
