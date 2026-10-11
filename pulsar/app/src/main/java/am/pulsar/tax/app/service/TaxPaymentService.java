package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.mapper.filter.TaxPaymentSearchMapper;
import am.pulsar.tax.app.repository.TaxPaymentRepository;
import am.pulsar.tax.app.service.provider.TaxPaymentProvider;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.create.TaxPaymentCreateDto;
import am.pulsar.tax.dto.enums.sort.TaxPaymentSort;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.filter.TaxPaymentFilterDto;
import am.pulsar.tax.dto.patch.TaxPaymentPatchDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

@Service
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxPaymentService extends AbstractCrudService<TaxPayment, TaxPaymentCreateDto, TaxPaymentPatchDto, TaxPaymentFilterDto> {

    public TaxPaymentService(TaxPaymentProvider entityProvider,
                     TaxPaymentRepository repository,
                     TaxPaymentSearchMapper searchMapper) {
        super(TaxPayment.class, entityProvider, repository, repository, searchMapper);
    }

    /** Записи за год: по дате уплаты. */
    public List<TaxPayment> getByYear(int year) {
        TaxPaymentFilterDto filter = TaxPaymentFilterDto.builder().years(Set.of(year)).build();
        return search(SearchRequest.of(filter, List.of(SearchRequest.Sort.of(TaxPaymentSort.DATE, SearchRequest.SortDirection.ASC),
                SearchRequest.Sort.of(TaxPaymentSort.ID, SearchRequest.SortDirection.ASC)))).getContent();
    }

    @Override
    protected void processEntityBeforeSaving(TaxPaymentCreateDto createDto, TaxPayment entity) {
        entity.setBusinessId(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Override
    protected boolean hasWriteAccess() {
        return AuthenticationHolder.getCurrentUserInfo().isPresent();
    }
}
