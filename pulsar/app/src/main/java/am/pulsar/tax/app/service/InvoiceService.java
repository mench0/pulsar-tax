package am.pulsar.tax.app.service;import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.DuplicateException;
import am.pulsar.tax.app.mapper.filter.InvoiceSearchMapper;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.InvoiceRepository;
import am.pulsar.tax.app.service.provider.InvoiceProvider;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.dto.create.InvoiceCreateDto;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.InvoiceStatus;
import am.pulsar.tax.dto.enums.sort.InvoiceSort;
import am.pulsar.tax.dto.filter.InvoiceFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.patch.InvoicePatchDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Счета на оплату. Без номера счёт получает следующий вида PT-2026-001. Отметка оплаты записывает доход
 * на сумму счёта; удаление счёта удаляет и этот доход (каскад в базе).
 */
@Service
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvoiceService extends AbstractCrudService<Invoice, InvoiceCreateDto, InvoicePatchDto, InvoiceFilterDto> {

    InvoiceRepository invoiceRepository;
    IncomeRepository incomeRepository;

    public InvoiceService(InvoiceProvider entityProvider,
                          InvoiceRepository repository,
                          InvoiceSearchMapper searchMapper,
                          IncomeRepository incomeRepository) {
        super(Invoice.class, entityProvider, repository, repository, searchMapper);
        this.invoiceRepository = repository;
        this.incomeRepository = incomeRepository;
    }

    /** Все счета: сначала новые. */
    public List<Invoice> getAll() {
        return search(SearchRequest.of(new InvoiceFilterDto(), InvoiceSort.DATE.getDefaultSorts())).getContent();
    }

    @Override
    protected void processEntityBeforeSaving(InvoiceCreateDto createDto, Invoice entity) {
        entity.setBusinessId(AuthenticationHolder.getCurrentBusinessIdOrElseThrow());
        if (entity.getNumber().isEmpty()) {
            entity.setNumber(nextNumber(entity.getBusinessId(), entity.getDate().getYear()));
        } else if (invoiceRepository.existsByBusinessIdAndNumberIgnoreCase(entity.getBusinessId(), entity.getNumber())) {
            throw new DuplicateException("Счёт с номером " + entity.getNumber() + " уже существует");
        }
    }

    private String nextNumber(long businessId, int year) {
        String prefix = "PT-" + year + "-";
        int max = invoiceRepository.numbersStartingWith(businessId, prefix).stream()
                .mapToInt(n -> {
                    try {
                        return Integer.parseInt(n.substring(prefix.length()));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max().orElse(0);
        return prefix + String.format("%03d", max + 1);
    }

    /** Отмечает счёт оплаченным и записывает доход на сумму счёта. Повторный вызов ничего не меняет. */
    @Transactional
    public Invoice markPaid(Long id, LocalDate paidOn) {
        Invoice invoice = getByIdOrElseThrow(id);
        assertWriteAccess(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice;
        }
        BigDecimal total = invoice.total();
        if (total.signum() <= 0) {
            throw new CustomValidationException("Сумма счёта равна нулю — доход не записать");
        }
        invoice.setStatus(InvoiceStatus.PAID);
        incomeRepository.save(Income.builder()
                .businessId(invoice.getBusinessId())
                .date(paidOn)
                .amount(total)
                .currency(Currency.AMD)
                .client(invoice.getClient())
                .description("Оплата по счёту " + invoice.getNumber())
                .invoiceId(invoice.getId())
                .build());
        return invoiceRepository.saveAndFlush(invoice);
    }

    @Override
    protected boolean hasWriteAccess() {
        return AuthenticationHolder.getCurrentUserInfo().isPresent();
    }
}
