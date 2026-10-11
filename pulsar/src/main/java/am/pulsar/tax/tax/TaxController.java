package am.pulsar.tax.tax;

import am.pulsar.tax.business.BusinessService;
import am.pulsar.tax.identity.AuthUser;
import am.pulsar.tax.shared.Requests;
import am.pulsar.tax.shared.Yerevan;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Правила, сводка и уплаты налога. */
@RestController
@RequestMapping("/api")
public class TaxController {

    private final TaxService tax;
    private final BusinessService businesses;
    private final Yerevan yerevan;

    public TaxController(TaxService tax, BusinessService businesses, Yerevan yerevan) {
        this.tax = tax;
        this.businesses = businesses;
        this.yerevan = yerevan;
    }

    /** Справочник доступен без входа: он нужен ещё до регистрации. */
    @GetMapping("/rules")
    Map<String, Object> rules(@RequestParam(required = false) String year) {
        return TaxRules.toJson(Requests.year(year, yerevan.today()));
    }

    @GetMapping("/summary")
    Map<String, Object> summary(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String year) {
        LocalDate today = yerevan.today();
        return tax.summary(businesses.forUser(user.id()), Requests.year(year, today), today);
    }

    @GetMapping("/payments")
    List<Map<String, Object>> payments(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String year) {
        long b = businesses.forUser(user.id()).getId();
        return tax.payments(b, Requests.year(year, yerevan.today())).stream().map(TaxPayment::toJson).toList();
    }

    @PostMapping("/payments")
    ResponseEntity<Map<String, Object>> addPayment(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(tax.addPayment(businesses.forUser(user.id()).getId(), body).toJson());
    }

    @DeleteMapping("/payments/{id}")
    ResponseEntity<Void> deletePayment(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        tax.deletePayment(businesses.forUser(user.id()).getId(), id);
        return ResponseEntity.noContent().build();
    }
}
