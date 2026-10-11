package am.pulsar.tax.app.controller;

import am.pulsar.tax.app.entity.InvoicePayment;
import am.pulsar.tax.app.service.ReconciliationService;
import am.pulsar.tax.app.service.ReconciliationService.Overview;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import am.pulsar.tax.app.utils.ClockUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Сверка счетов и поступлений: открытые счета, задолженность, несопоставленные поступления, подсказки. */
@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReconciliationController {

    ReconciliationService reconciliationService;

    @GetMapping
    public Overview overview() {
        return reconciliationService.overview(business(), ClockUtils.today());
    }

    /** Доход закрывает счёт (incomeId) или возврат покупателю уменьшает оплату (bankTransactionId). */
    public record AllocationRequest(Long invoiceId, Long incomeId, Long bankTransactionId, BigDecimal amount, Boolean fromSuggestion) {
    }

    @PostMapping("/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoicePayment allocate(@RequestBody AllocationRequest r) {
        if (r.bankTransactionId() != null) return reconciliationService.allocateRefund(business(), r.invoiceId(), r.bankTransactionId());
        return reconciliationService.allocate(business(), r.invoiceId(), r.incomeId(), r.amount(),
                Boolean.TRUE.equals(r.fromSuggestion()) ? "SUGGESTION" : "MANUAL");
    }

    @DeleteMapping("/allocations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        reconciliationService.remove(business(), id);
    }

    private static Long business() {
        return AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
    }
}
