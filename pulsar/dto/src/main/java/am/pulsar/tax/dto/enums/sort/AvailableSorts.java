package am.pulsar.tax.dto.enums.sort;import am.pulsar.tax.dto.BaseDto;
import am.pulsar.tax.dto.filter.SearchRequest.Sort;

import java.util.List;

public interface AvailableSorts<D extends BaseDto, S extends AvailableSorts<D, S>> {

    List<Sort<S>> getDefaultSorts();
}
