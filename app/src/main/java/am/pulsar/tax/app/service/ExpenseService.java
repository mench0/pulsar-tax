package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.mapper.filter.ExpenseSearchMapper;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.service.provider.ExpenseProvider;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.create.ExpenseCreateDto;
import am.pulsar.tax.dto.enums.sort.ExpenseSort;
import am.pulsar.tax.dto.filter.ExpenseFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.patch.ExpensePatchDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExpenseService extends AbstractCrudService<Expense, ExpenseCreateDto, ExpensePatchDto, ExpenseFilterDto> {

    public ExpenseService(ExpenseProvider entityProvider,
                     ExpenseRepository repository,
                     ExpenseSearchMapper searchMapper) {
        super(Expense.class, entityProvider, repository, repository, searchMapper);
    }

    /** Записи за год: сначала новые. */
    public List<Expense> getByYear(int year) {
        ExpenseFilterDto filter = ExpenseFilterDto.builder().year(year).build();
        return search(SearchRequest.of(filter, ExpenseSort.DATE.getDefaultSorts())).getContent();
    }

    @Override
    protected void processEntityBeforeSaving(ExpenseCreateDto createDto, Expense entity) {
        entity.setBusinessId(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
    }

    @Override
    protected boolean hasWriteAccess() {
        return AuthenticationHolder.getCurrentUserInfo().isPresent();
    }
}
