package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.mapper.entity.InvoiceMapper;
import am.pulsar.tax.app.service.InvoiceService;
import am.pulsar.tax.app.utils.RequestUtils;
import am.pulsar.tax.dto.InvoiceDto;
import am.pulsar.tax.dto.create.InvoiceCreateDto;
import am.pulsar.tax.dto.enums.sort.InvoiceSort;
import am.pulsar.tax.dto.filter.InvoiceFilterDto;
import am.pulsar.tax.dto.patch.InvoicePatchDto;
import am.pulsar.tax.dto.request.EInvoiceRequestDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/** Счета: общие операции (AbstractCrudController), отметка оплаты и номер e-invoice. */
@RestController
@RequestMapping("/api/invoices")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvoiceController extends
        AbstractCrudController<Invoice, InvoiceDto, InvoiceCreateDto, InvoicePatchDto, InvoiceFilterDto, InvoiceSort> {

    InvoiceService invoiceService;

    public InvoiceController(InvoiceService service, InvoiceMapper mapper) {
        super(service, mapper);
        this.invoiceService = service;
    }

    /** Все счета, сначала новые. */
    @GetMapping
    public List<InvoiceDto> getAll() {
        return mapper.toDtoList(invoiceService.getAll());
    }

    /**
     * Отметить оплату (по умолчанию сегодня): записывается доход на сумму счёта. С incomeId счёт закрывается уже
     * учтённым доходом; если похожий доход уже есть, без newPayment=true ответ 409 — чтобы не учесть оплату дважды.
     */
    @PostMapping("/{id}/paid")
    public InvoiceDto markPaid(@PathVariable("id") Long id, @RequestParam(required = false) String date,
                               @RequestParam(required = false) Long incomeId,
                               @RequestParam(required = false) Boolean newPayment) {
        return mapper.toDto(invoiceService.markPaid(id, RequestUtils.date(date), incomeId, Boolean.TRUE.equals(newPayment)));
    }

    /** Номер официального электронного счёта из e-invoice.taxservice.am. */
    @PutMapping("/{id}/einvoice")
    public InvoiceDto setEInvoiceNumber(@PathVariable("id") Long id, @RequestBody EInvoiceRequestDto request) {
        String number = request.getEInvoiceNumber() == null ? "" : request.getEInvoiceNumber();
        return mapper.toDto(invoiceService.patch(InvoicePatchDto.builder().id(id).eInvoiceNumber(Optional.of(number)).build()));
    }
}
