package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.mapper.entity.TaxPaymentMapper;
import am.pulsar.tax.app.service.TaxPaymentService;
import am.pulsar.tax.app.utils.RequestUtils;
import am.pulsar.tax.dto.TaxPaymentDto;
import am.pulsar.tax.dto.create.TaxPaymentCreateDto;
import am.pulsar.tax.dto.enums.sort.TaxPaymentSort;
import am.pulsar.tax.dto.filter.TaxPaymentFilterDto;
import am.pulsar.tax.dto.patch.TaxPaymentPatchDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Уплаты налога: общие операции (AbstractCrudController) и список за налоговый год. */
@RestController
@RequestMapping("/api/payments")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxPaymentController extends
        AbstractCrudController<TaxPayment, TaxPaymentDto, TaxPaymentCreateDto, TaxPaymentPatchDto, TaxPaymentFilterDto, TaxPaymentSort> {

    TaxPaymentService taxPaymentService;

    public TaxPaymentController(TaxPaymentService service, TaxPaymentMapper mapper) {
        super(service, mapper);
        this.taxPaymentService = service;
    }

    /** Записи за год (по умолчанию текущий). */
    @GetMapping
    public List<TaxPaymentDto> getByYear(@RequestParam(required = false) String year) {
        return mapper.toDtoList(taxPaymentService.getByYear(RequestUtils.year(year)));
    }
}
