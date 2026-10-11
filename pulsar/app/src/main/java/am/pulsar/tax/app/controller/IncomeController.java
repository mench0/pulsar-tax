package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.mapper.entity.IncomeMapper;
import am.pulsar.tax.app.service.IncomeService;
import am.pulsar.tax.app.utils.RequestUtils;
import am.pulsar.tax.dto.ImportResultDto;
import am.pulsar.tax.dto.IncomeDto;
import am.pulsar.tax.dto.create.IncomeCreateDto;
import am.pulsar.tax.dto.create.IncomeImportDto;
import am.pulsar.tax.dto.enums.sort.IncomeSort;
import am.pulsar.tax.dto.filter.IncomeFilterDto;
import am.pulsar.tax.dto.patch.IncomePatchDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Доходы: общие операции (AbstractCrudController), список за год и загрузка выписки. */
@RestController
@RequestMapping("/api/incomes")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IncomeController extends
        AbstractCrudController<Income, IncomeDto, IncomeCreateDto, IncomePatchDto, IncomeFilterDto, IncomeSort> {

    IncomeService incomeService;

    public IncomeController(IncomeService service, IncomeMapper mapper) {
        super(service, mapper);
        this.incomeService = service;
    }

    /** Доходы за год (по умолчанию текущий), сначала новые. */
    @GetMapping
    public List<IncomeDto> getByYear(@RequestParam(required = false) String year) {
        return mapper.toDtoList(incomeService.getByYear(RequestUtils.year(year)));
    }

    /** Пакет доходов из выписки; строки с уже загруженным importKey пропускаются. */
    @PostMapping("/import")
    public ImportResultDto importIncomes(@RequestBody IncomeImportDto importDto) {
        return incomeService.importIncomes(importDto);
    }
}
