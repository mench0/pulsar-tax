package am.pulsar.tax.invoicing;

import am.pulsar.tax.business.BusinessService;
import am.pulsar.tax.identity.AuthUser;
import am.pulsar.tax.shared.Requests;
import am.pulsar.tax.shared.Yerevan;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoices;
    private final BusinessService businesses;
    private final Yerevan yerevan;

    public InvoiceController(InvoiceService invoices, BusinessService businesses, Yerevan yerevan) {
        this.invoices = invoices;
        this.businesses = businesses;
        this.yerevan = yerevan;
    }

    private long business(AuthUser user) {
        return businesses.forUser(user.id()).getId();
    }

    @GetMapping
    List<Map<String, Object>> list(@AuthenticationPrincipal AuthUser user) {
        return invoices.list(business(user)).stream().map(Invoice::toJson).toList();
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> add(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(invoices.add(business(user), body).toJson());
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return invoices.get(business(user), id).toJson();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        invoices.delete(business(user), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/paid")
    Map<String, Object> paid(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                             @RequestParam(required = false) String date) {
        return invoices.markPaid(business(user), id, Requests.date(date, yerevan.today())).toJson();
    }

    @PutMapping("/{id}/einvoice")
    Map<String, Object> eInvoice(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                 @RequestBody Map<String, Object> body) {
        return invoices.setEInvoiceNumber(business(user), id, body).toJson();
    }
}
