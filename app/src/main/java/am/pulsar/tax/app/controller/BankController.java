package am.pulsar.tax.app.controller;

import am.pulsar.tax.app.entity.StatementImport;
import am.pulsar.tax.app.service.BankService;
import am.pulsar.tax.app.utils.RequestUtils;
import am.pulsar.tax.dto.bank.BankTransactionDto;
import am.pulsar.tax.dto.bank.StatementImportResultDto;
import am.pulsar.tax.dto.bank.StatementPreviewDto;
import am.pulsar.tax.dto.bank.StatementUploadDto;
import am.pulsar.tax.dto.bank.TransactionReviewDto;
import am.pulsar.tax.dto.enums.TransactionStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Банковские выписки и операции: просмотр до сохранения, сохранение, проверка типов. */
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BankController {

    BankService bankService;

    /** Проверка выписки без сохранения: ошибки и предупреждения по строкам, дубли, подсказки типа. */
    @PostMapping("/statements/preview")
    public StatementPreviewDto preview(@RequestBody StatementUploadDto upload) {
        return bankService.preview(upload);
    }

    @PostMapping("/statements")
    @ResponseStatus(HttpStatus.CREATED)
    public StatementImportResultDto commit(@RequestBody StatementUploadDto upload) {
        return bankService.commit(upload);
    }

    @GetMapping("/statements")
    public List<Map<String, Object>> statements() {
        return bankService.imports().stream().map(BankController::info).toList();
    }

    @GetMapping("/statements/{id}/transactions")
    public List<BankTransactionDto> statementRows(@PathVariable Long id) {
        return bankService.importRows(id);
    }

    @PostMapping("/statements/{id}/reclassify")
    public Map<String, Integer> reclassify(@PathVariable Long id) {
        return Map.of("updated", bankService.reclassify(id));
    }

    /** Операции за период и/или в статусе (NEEDS_REVIEW — очередь проверки). */
    @GetMapping("/transactions")
    public List<BankTransactionDto> transactions(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 @RequestParam(required = false) String status) {
        TransactionStatus st = status == null || status.isBlank() ? null : TransactionStatus.valueOf(status);
        return bankService.list(from == null ? null : RequestUtils.date(from), to == null ? null : RequestUtils.date(to), st);
    }

    @PostMapping("/transactions/{id}/review")
    public BankTransactionDto review(@PathVariable Long id, @RequestBody TransactionReviewDto review) {
        return bankService.review(id, review);
    }

    public record ConfirmRequest(List<Long> ids) {
    }

    /** Подтвердить уверенные подсказки (все или только перечисленные). */
    @PostMapping("/transactions/confirm-confident")
    public Map<String, Integer> confirmConfident(@RequestBody(required = false) ConfirmRequest request) {
        return Map.of("confirmed", bankService.confirmConfident(request == null ? null : request.ids()));
    }

    private static Map<String, Object> info(StatementImport i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("fileName", i.getFileName());
        m.put("account", i.getAccount());
        m.put("createdAt", i.getCreatedAt());
        m.put("rowsTotal", i.getRowsTotal());
        m.put("rowsAdded", i.getRowsAdded());
        m.put("rowsDuplicate", i.getRowsDuplicate());
        m.put("rowsError", i.getRowsError());
        m.put("rowsSkipped", i.getRowsSkipped());
        return m;
    }
}
