package am.pulsar.tax.app.controller;import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.entity.TaxPayment;
import am.pulsar.tax.app.mapper.entity.BusinessMapper;
import am.pulsar.tax.app.mapper.entity.ExpenseMapper;
import am.pulsar.tax.app.mapper.entity.IncomeMapper;
import am.pulsar.tax.app.mapper.entity.InvoiceMapper;
import am.pulsar.tax.app.mapper.entity.TaxPaymentMapper;
import am.pulsar.tax.app.service.BusinessService;
import am.pulsar.tax.app.service.ExpenseService;
import am.pulsar.tax.app.service.IncomeService;
import am.pulsar.tax.app.service.InvoiceService;
import am.pulsar.tax.app.service.TaxPaymentService;
import am.pulsar.tax.app.utils.CsvWriter;
import am.pulsar.tax.dto.enums.sort.AvailableSorts;
import am.pulsar.tax.dto.enums.sort.ExpenseSort;
import am.pulsar.tax.dto.enums.sort.IncomeSort;
import am.pulsar.tax.dto.enums.sort.InvoiceSort;
import am.pulsar.tax.dto.enums.sort.TaxPaymentSort;
import am.pulsar.tax.dto.filter.ExpenseFilterDto;
import am.pulsar.tax.dto.filter.IncomeFilterDto;
import am.pulsar.tax.dto.filter.InvoiceFilterDto;
import am.pulsar.tax.dto.filter.SearchRequest.SortDirection;
import am.pulsar.tax.dto.filter.SearchRequest;
import am.pulsar.tax.dto.filter.TaxPaymentFilterDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Выгрузка всех данных ИП в JSON и таблиц в CSV для Excel или бухгалтера. */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExportController {

    static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    BusinessService businessService;
    IncomeService incomeService;
    ExpenseService expenseService;
    InvoiceService invoiceService;
    TaxPaymentService taxPaymentService;
    BusinessMapper businessMapper;
    IncomeMapper incomeMapper;
    ExpenseMapper expenseMapper;
    InvoiceMapper invoiceMapper;
    TaxPaymentMapper taxPaymentMapper;
    ImportProfileController importProfileController;

    @GetMapping("/data.json")
    public ResponseEntity<Map<String, Object>> data() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 3);
        root.put("profile", businessMapper.toDto(businessService.getCurrent()));
        root.put("incomes", incomeMapper.toDtoList(incomes()));
        root.put("invoices", invoiceMapper.toDtoList(invoices()));
        root.put("payments", taxPaymentMapper.toDtoList(payments()));
        root.put("expenses", expenseMapper.toDtoList(expenses()));
        root.put("importProfiles", importProfileController.getAll());
        return attachment("pulsar-tax.json", MediaType.APPLICATION_JSON, root);
    }

    @GetMapping("/incomes.csv")
    public ResponseEntity<String> incomesCsv() {
        return attachment("incomes.csv", CSV, CsvWriter.incomes(incomes()));
    }

    @GetMapping("/invoices.csv")
    public ResponseEntity<String> invoicesCsv() {
        return attachment("invoices.csv", CSV, CsvWriter.invoices(invoices().reversed()));
    }

    @GetMapping("/expenses.csv")
    public ResponseEntity<String> expensesCsv() {
        return attachment("expenses.csv", CSV, CsvWriter.expenses(expenses()));
    }

    /** Все записи от старых к новым. */
    private List<Income> incomes() {
        return incomeService.search(SearchRequest.of(new IncomeFilterDto(), ascending(IncomeSort.DATE, IncomeSort.ID))).getContent();
    }

    private List<Expense> expenses() {
        return expenseService.search(SearchRequest.of(new ExpenseFilterDto(), ascending(ExpenseSort.DATE, ExpenseSort.ID))).getContent();
    }

    private List<Invoice> invoices() {
        return invoiceService.search(SearchRequest.of(new InvoiceFilterDto(), ascending(InvoiceSort.DATE, InvoiceSort.ID))).getContent();
    }

    private List<TaxPayment> payments() {
        return taxPaymentService.search(SearchRequest.of(new TaxPaymentFilterDto(),
                ascending(TaxPaymentSort.DATE, TaxPaymentSort.ID))).getContent();
    }

    @SafeVarargs
    private static <S extends AvailableSorts<?, S>> List<SearchRequest.Sort<S>> ascending(S... sorts) {
        return Arrays.stream(sorts).map(s -> SearchRequest.Sort.of(s, SortDirection.ASC)).toList();
    }

    private static <T> ResponseEntity<T> attachment(String name, MediaType type, T body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(body);
    }
}
