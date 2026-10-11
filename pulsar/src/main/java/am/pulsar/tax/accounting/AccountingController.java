package am.pulsar.tax.accounting;

import am.pulsar.tax.business.BusinessService;
import am.pulsar.tax.identity.AuthUser;
import am.pulsar.tax.shared.Requests;
import am.pulsar.tax.shared.Yerevan;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Доходы, импорт выписок, расходы и сохранённые колонки выписок. */
@RestController
@RequestMapping("/api")
public class AccountingController {

    private final AccountingService accounting;
    private final BusinessService businesses;
    private final Yerevan yerevan;

    public AccountingController(AccountingService accounting, BusinessService businesses, Yerevan yerevan) {
        this.accounting = accounting;
        this.businesses = businesses;
        this.yerevan = yerevan;
    }

    private long business(AuthUser user) {
        return businesses.forUser(user.id()).getId();
    }

    @GetMapping("/incomes")
    List<Map<String, Object>> incomes(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String year) {
        return accounting.incomes(business(user), Requests.year(year, yerevan.today())).stream().map(Income::toJson).toList();
    }

    @PostMapping("/incomes")
    ResponseEntity<Map<String, Object>> addIncome(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(accounting.addIncome(business(user), body).toJson());
    }

    @PostMapping("/incomes/import")
    Map<String, Object> importIncomes(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body) {
        return accounting.importIncomes(business(user), body);
    }

    @DeleteMapping("/incomes/{id}")
    ResponseEntity<Void> deleteIncome(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        accounting.deleteIncome(business(user), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expenses")
    List<Map<String, Object>> expenses(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String year) {
        return accounting.expenses(business(user), Requests.year(year, yerevan.today())).stream().map(Expense::toJson).toList();
    }

    @PostMapping("/expenses")
    ResponseEntity<Map<String, Object>> addExpense(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(accounting.addExpense(business(user), body).toJson());
    }

    @DeleteMapping("/expenses/{id}")
    ResponseEntity<Void> deleteExpense(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        accounting.deleteExpense(business(user), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-profiles")
    Map<String, Object> importProfiles(@AuthenticationPrincipal AuthUser user) {
        return accounting.importProfiles(business(user));
    }

    /** Отпечаток заголовка выписки приложение считает само. */
    @PutMapping("/import-profiles/{signature:[0-9a-z-]{4,64}}")
    ResponseEntity<Void> saveImportProfile(@AuthenticationPrincipal AuthUser user, @PathVariable String signature,
                                           @RequestBody Map<String, Object> body) {
        accounting.saveImportProfile(business(user), signature, body);
        return ResponseEntity.noContent().build();
    }
}
