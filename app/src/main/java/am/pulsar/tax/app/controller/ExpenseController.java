package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.mapper.entity.ExpenseMapper;
import am.pulsar.tax.app.service.ExpenseService;
import am.pulsar.tax.app.utils.RequestUtils;
import am.pulsar.tax.dto.ExpenseDto;
import am.pulsar.tax.dto.create.ExpenseCreateDto;
import am.pulsar.tax.dto.enums.sort.ExpenseSort;
import am.pulsar.tax.dto.filter.ExpenseFilterDto;
import am.pulsar.tax.dto.patch.ExpensePatchDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Расходы: общие операции (AbstractCrudController) и список за год. */
@RestController
@RequestMapping("/api/expenses")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExpenseController extends
        AbstractCrudController<Expense, ExpenseDto, ExpenseCreateDto, ExpensePatchDto, ExpenseFilterDto, ExpenseSort> {

    ExpenseService expenseService;

    public ExpenseController(ExpenseService service, ExpenseMapper mapper) {
        super(service, mapper);
        this.expenseService = service;
    }

    /** Записи за год (по умолчанию текущий). */
    @GetMapping
    public List<ExpenseDto> getByYear(@RequestParam(required = false) String year) {
        return mapper.toDtoList(expenseService.getByYear(RequestUtils.year(year)));
    }
}
