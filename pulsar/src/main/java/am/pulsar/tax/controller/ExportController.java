package am.pulsar.tax.controller;

import am.pulsar.tax.domain.Csv;
import am.pulsar.tax.entity.Business;
import am.pulsar.tax.entity.Expense;
import am.pulsar.tax.entity.Income;
import am.pulsar.tax.entity.Invoice;
import am.pulsar.tax.entity.TaxPayment;
import am.pulsar.tax.security.AuthUser;
import am.pulsar.tax.service.AccountingService;
import am.pulsar.tax.service.BusinessService;
import am.pulsar.tax.service.InvoiceService;
import am.pulsar.tax.service.TaxService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Выгрузка всех данных в JSON и таблиц в CSV для Excel или бухгалтера. */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final BusinessService businesses;
    private final AccountingService accounting;
    private final InvoiceService invoices;
    private final TaxService tax;

    public ExportController(BusinessService businesses, AccountingService accounting, InvoiceService invoices,
                            TaxService tax) {
        this.businesses = businesses;
        this.accounting = accounting;
        this.invoices = invoices;
        this.tax = tax;
    }

    @GetMapping("/data.json")
    ResponseEntity<Map<String, Object>> data(@AuthenticationPrincipal AuthUser user) {
        Business b = businesses.forUser(user.id());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 3);
        root.put("profile", b.toJson());
        root.put("incomes", accounting.allIncomes(b.getId()).stream().map(Income::toJson).toList());
        root.put("invoices", invoices.list(b.getId()).reversed().stream().map(Invoice::toJson).toList());
        root.put("payments", tax.allPayments(b.getId()).stream().map(TaxPayment::toJson).toList());
        root.put("expenses", accounting.allExpenses(b.getId()).stream().map(Expense::toJson).toList());
        root.put("importProfiles", accounting.importProfiles(b.getId()));
        return attachment("pulsar-tax.json", MediaType.APPLICATION_JSON, root);
    }

    @GetMapping("/incomes.csv")
    ResponseEntity<String> incomes(@AuthenticationPrincipal AuthUser user) {
        return attachment("incomes.csv", CSV, Csv.incomes(accounting.allIncomes(businesses.forUser(user.id()).getId())));
    }

    @GetMapping("/invoices.csv")
    ResponseEntity<String> invoices(@AuthenticationPrincipal AuthUser user) {
        return attachment("invoices.csv", CSV, Csv.invoices(invoices.list(businesses.forUser(user.id()).getId())));
    }

    @GetMapping("/expenses.csv")
    ResponseEntity<String> expenses(@AuthenticationPrincipal AuthUser user) {
        return attachment("expenses.csv", CSV, Csv.expenses(accounting.allExpenses(businesses.forUser(user.id()).getId())));
    }

    private static <T> ResponseEntity<T> attachment(String name, MediaType type, T body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(body);
    }
}
