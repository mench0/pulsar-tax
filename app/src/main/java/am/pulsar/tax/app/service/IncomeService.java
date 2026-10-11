package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.DuplicateException;
import am.pulsar.tax.app.mapper.filter.IncomeSearchMapper;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.InvoiceRepository;
import am.pulsar.tax.app.service.provider.IncomeProvider;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.ImportResultDto;
import am.pulsar.tax.dto.create.IncomeCreateDto;
import am.pulsar.tax.dto.create.IncomeImportDto;
import am.pulsar.tax.dto.enums.InvoiceStatus;
import am.pulsar.tax.dto.enums.sort.IncomeSort;
import am.pulsar.tax.dto.filter.IncomeFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.patch.IncomePatchDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IncomeService extends AbstractCrudService<Income, IncomeCreateDto, IncomePatchDto, IncomeFilterDto> {

    IncomeRepository incomeRepository;
    InvoiceRepository invoiceRepository;
    Validator validator;
    BankService bankService;

    public IncomeService(IncomeProvider entityProvider,
                         IncomeRepository repository,
                         IncomeSearchMapper searchMapper,
                         InvoiceRepository invoiceRepository,
                         Validator validator,
                         BankService bankService) {
        super(Income.class, entityProvider, repository, repository, searchMapper);
        this.incomeRepository = repository;
        this.invoiceRepository = invoiceRepository;
        this.validator = validator;
        this.bankService = bankService;
    }

    /** Доходы за год: сначала новые. */
    public List<Income> getByYear(int year) {
        IncomeFilterDto filter = IncomeFilterDto.builder().year(year).build();
        return search(SearchRequest.of(filter, IncomeSort.DATE.getDefaultSorts())).getContent();
    }

    @Override
    protected void processEntityBeforeSaving(IncomeCreateDto createDto, Income entity) {
        entity.setBusinessId(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
        if (entity.getImportKey() != null && incomeRepository.importKeys(entity.getBusinessId()).contains(entity.getImportKey())) {
            throw new DuplicateException("Эта строка выписки уже загружена");
        }
    }

    /**
     * Доходы из выписки. Строки с уже известным importKey пропускаются, поэтому одну и ту же выписку
     * можно загрузить повторно. Ошибка в любой строке отменяет загрузку целиком.
     */
    @Transactional
    public ImportResultDto importIncomes(@Valid IncomeImportDto importDto) {
        assertWriteAccess();
        Long businessId = AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
        List<Income> parsed = new ArrayList<>();
        List<IncomeCreateDto> items = importDto.getItems();
        for (int n = 0; n < items.size(); n++) {
            try {
                parsed.add(parseRow(items.get(n)));
            } catch (CustomValidationException e) {
                throw new CustomValidationException("Строка " + (n + 1) + ": " + e.getMessage());
            }
        }
        Set<String> known = incomeRepository.importKeys(businessId);
        List<Income> fresh = new ArrayList<>();
        for (Income income : parsed) {
            if (income.getImportKey() == null || known.add(income.getImportKey())) {
                income.setBusinessId(businessId);
                fresh.add(income);
            }
        }
        incomeRepository.saveAll(fresh);
        return new ImportResultDto(fresh.size(), parsed.size() - fresh.size());
    }

    private Income parseRow(IncomeCreateDto row) {
        if (row == null) {
            throw new CustomValidationException("неверный формат");
        }
        Set<ConstraintViolation<IncomeCreateDto>> violations = validator.validate(row);
        if (!violations.isEmpty()) {
            throw new CustomValidationException(violations.stream()
                    .min(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .orElseThrow().getMessage());
        }
        return entityProvider.create(row);
    }

    /** Без дохода по счёту счёт снова считается неоплаченным: оплату можно отметить заново. */
    @Override
    protected void processEntitiesBeforeDeleting(List<Income> entities) {
        Set<Long> invoiceIds = entities.stream().map(Income::getInvoiceId).filter(Objects::nonNull).collect(Collectors.toSet());
        invoiceRepository.findAllById(invoiceIds).forEach((Invoice invoice) -> invoice.setStatus(InvoiceStatus.SENT));
        // Доход из банковской операции удалён — операция снова ждёт решения пользователя.
        bankService.detachIncomes(AuthenticationHolder.getCurrentBusinessIdOrElseThrow(),
                entities.stream().map(Income::getId).toList());
    }

    @Override
    protected boolean hasWriteAccess() {
        return AuthenticationHolder.getCurrentUserInfo().isPresent();
    }
}
