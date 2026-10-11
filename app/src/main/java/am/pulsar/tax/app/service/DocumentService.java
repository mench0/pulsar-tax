package am.pulsar.tax.app.service;

import am.pulsar.tax.app.entity.BankTransaction;
import am.pulsar.tax.app.entity.Document;
import am.pulsar.tax.app.entity.DocumentFile;
import am.pulsar.tax.app.entity.Expense;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.DuplicateException;
import am.pulsar.tax.app.exception.EntityNotFoundByIdException;
import am.pulsar.tax.app.repository.BankTransactionRepository;
import am.pulsar.tax.app.repository.DocumentFileRepository;
import am.pulsar.tax.app.repository.DocumentRepository;
import am.pulsar.tax.app.repository.ExpenseRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.InvoiceRepository;
import am.pulsar.tax.app.service.document.DocumentFields;
import am.pulsar.tax.app.service.document.TextExtractor;
import am.pulsar.tax.dto.enums.Currency;
import am.pulsar.tax.dto.enums.ExpenseCategory;
import am.pulsar.tax.dto.enums.TransactionKind;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Документы (счета поставщиков, чеки, акты): загрузка, распознавание реквизитов, подтверждение и связь
 * с операциями. Распознанные значения — только подсказка: в учёт попадают значения, подтверждённые пользователем.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentService {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    static final Set<String> TYPES = Set.of("application/pdf", "image/png", "image/jpeg");
    static final Set<String> DOC_TYPES = Set.of("SUPPLIER_INVOICE", "RECEIPT", "ACT", "CONTRACT", "OTHER");

    DocumentRepository documents;
    DocumentFileRepository files;
    ExpenseRepository expenses;
    IncomeRepository incomes;
    InvoiceRepository invoices;
    BankTransactionRepository transactions;
    TextExtractor extractor;
    JsonMapper json;

    /** Документ без содержимого файла и текста — для списка и карточки. */
    public record DocumentInfo(Long id, String fileName, String contentType, int sizeBytes, String extraction,
                               Map<String, Object> extracted, String status, String docType, String number,
                               LocalDate docDate, String currency, BigDecimal amount, String counterparty,
                               String counterpartyTin, Long expenseId, Long incomeId, Long invoiceId,
                               Long bankTransactionId, List<Map<String, Object>> matches, Object createdAt) {
    }

    /** Подтверждённые пользователем значения и связи. createExpense — завести расход по документу. */
    public record ConfirmRequest(String docType, String number, String date, String currency, BigDecimal amount,
                                 String counterparty, String counterpartyTin, Long expenseId, Long incomeId,
                                 Long invoiceId, Long bankTransactionId, Boolean createExpense, String expenseCategory) {
    }

    @Transactional
    public DocumentInfo upload(Long businessId, String fileName, byte[] content) {
        if (content == null || content.length == 0) throw new CustomValidationException("Файл пустой");
        if (content.length > MAX_BYTES) throw new CustomValidationException("Файл больше 10 МБ");
        String type = sniff(content);
        if (type == null || !TYPES.contains(type)) {
            throw new CustomValidationException("Поддерживаются PDF, PNG и JPEG");
        }
        String sha = sha256(content);
        documents.findByBusinessIdAndSha256(businessId, sha).ifPresent(d -> {
            throw new DuplicateException("Этот файл уже загружен (документ №" + d.getId() + ")");
        });
        TextExtractor.Result r = extractor.extract(content, type);
        Map<String, DocumentFields.Field> fields = DocumentFields.extract(r.text());
        String name = fileName == null || fileName.isBlank() ? "document" : fileName.strip();
        name = name.replaceAll("[\\\\/\\r\\n\"]", "_");
        Document d = documents.save(Document.builder()
                .businessId(businessId)
                .fileName(name.length() > 255 ? name.substring(0, 255) : name)
                .contentType(type).sizeBytes(content.length).sha256(sha)
                .textContent(r.text().length() > 100_000 ? r.text().substring(0, 100_000) : r.text())
                .extraction(r.method())
                .extracted(json.writeValueAsString(fields))
                .build());
        files.save(new DocumentFile(d.getId(), content));
        return info(d);
    }

    @Transactional(readOnly = true)
    public List<DocumentInfo> list(Long businessId) {
        return documents.findByBusinessIdOrderByCreatedAtDescIdDesc(businessId).stream().map(this::info).toList();
    }

    @Transactional(readOnly = true)
    public DocumentInfo get(Long businessId, Long id) {
        return info(find(businessId, id));
    }

    public record FileContent(String fileName, String contentType, byte[] content) {
    }

    @Transactional(readOnly = true)
    public FileContent file(Long businessId, Long id) {
        Document d = find(businessId, id);
        return new FileContent(d.getFileName(), d.getContentType(),
                files.findById(d.getId()).map(DocumentFile::getContent).orElse(new byte[0]));
    }

    @Transactional
    public DocumentInfo confirm(Long businessId, Long id, ConfirmRequest req) {
        Document d = find(businessId, id);
        if (req == null) throw new CustomValidationException("Нужны подтверждённые значения");
        String docType = req.docType() == null || req.docType().isBlank() ? d.getDocType() : req.docType();
        if (!DOC_TYPES.contains(docType)) throw new CustomValidationException("Неизвестный тип документа: " + docType);
        LocalDate date = req.date() == null || req.date().isBlank() ? null : am.pulsar.tax.app.utils.RequestUtils.date(req.date());
        Currency currency = null;
        if (req.currency() != null && !req.currency().isBlank()) {
            try {
                currency = Currency.valueOf(req.currency());
            } catch (IllegalArgumentException e) {
                throw new CustomValidationException("Неизвестная валюта: " + req.currency());
            }
        }
        if (req.amount() != null && req.amount().signum() <= 0) throw new CustomValidationException("Сумма должна быть больше нуля");
        if (req.amount() != null && req.amount().stripTrailingZeros().scale() > 2) throw new CustomValidationException("Не больше двух знаков после запятой");

        // Связи проверяются на принадлежность этому ИП.
        if (req.expenseId() != null) expenses.findById(req.expenseId()).filter(e -> e.getBusinessId().equals(businessId))
                .orElseThrow(() -> new EntityNotFoundByIdException(Expense.class, req.expenseId()));
        if (req.incomeId() != null) incomes.findById(req.incomeId()).filter(e -> e.getBusinessId().equals(businessId))
                .orElseThrow(() -> new EntityNotFoundByIdException(am.pulsar.tax.app.entity.Income.class, req.incomeId()));
        if (req.invoiceId() != null) invoices.findById(req.invoiceId()).filter(e -> e.getBusinessId().equals(businessId))
                .orElseThrow(() -> new EntityNotFoundByIdException(am.pulsar.tax.app.entity.Invoice.class, req.invoiceId()));
        BankTransaction tx = null;
        if (req.bankTransactionId() != null) {
            tx = transactions.findByIdAndBusinessId(req.bankTransactionId(), businessId)
                    .orElseThrow(() -> new EntityNotFoundByIdException(BankTransaction.class, req.bankTransactionId()));
        }

        d.setDocType(docType);
        d.setNumber(req.number() == null ? "" : trim(req.number(), 100));
        d.setDocDate(date);
        d.setCurrency(currency == null ? null : currency.name());
        d.setAmount(req.amount());
        d.setCounterparty(req.counterparty() == null ? "" : trim(req.counterparty(), 200));
        d.setCounterpartyTin(req.counterpartyTin() == null ? "" : trim(req.counterpartyTin(), 20));
        d.setIncomeId(req.incomeId());
        d.setInvoiceId(req.invoiceId());
        d.setBankTransactionId(req.bankTransactionId());
        Long expenseId = req.expenseId();
        // Одна оплата — один расход: если операция банка уже стала расходом, документ привязывается к нему.
        if (expenseId == null && tx != null && tx.getExpenseId() != null) expenseId = tx.getExpenseId();
        if (Boolean.TRUE.equals(req.createExpense()) && expenseId == null) {
            if (tx != null && tx.getKind() != null && tx.getKind() != TransactionKind.EXPENSE && tx.getKind() != TransactionKind.BANK_FEE) {
                throw new CustomValidationException("Связанная операция банка не расход — расход по документу не создаётся");
            }
            if (req.amount() == null || date == null) throw new CustomValidationException("Для расхода нужны дата и сумма");
            if (currency != null && currency != Currency.AMD) {
                throw new CustomValidationException("Расход в валюте заведите через банковскую операцию или вручную с суммой в драмах");
            }
            ExpenseCategory cat = ExpenseCategory.OTHER;
            if (req.expenseCategory() != null && !req.expenseCategory().isBlank()) {
                try {
                    cat = ExpenseCategory.valueOf(req.expenseCategory());
                } catch (IllegalArgumentException e) {
                    throw new CustomValidationException("Неизвестная категория расхода: " + req.expenseCategory());
                }
            }
            String desc = (d.getCounterparty().isBlank() ? "" : d.getCounterparty() + " — ")
                    + (d.getNumber().isBlank() ? d.getFileName() : "документ " + d.getNumber());
            expenseId = expenses.save(Expense.builder().businessId(businessId).date(date).amount(req.amount())
                    .category(cat).description(trim(desc, 500)).build()).getId();
        }
        d.setExpenseId(expenseId);
        d.setStatus("CONFIRMED");
        return info(documents.save(d));
    }

    @Transactional
    public DocumentInfo reject(Long businessId, Long id) {
        Document d = find(businessId, id);
        d.setStatus("REJECTED");
        return info(documents.save(d));
    }

    @Transactional
    public void delete(Long businessId, Long id) {
        documents.delete(find(businessId, id));
    }

    private Document find(Long businessId, Long id) {
        return documents.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new EntityNotFoundByIdException(Document.class, id));
    }

    @SuppressWarnings("unchecked")
    private DocumentInfo info(Document d) {
        Map<String, Object> extracted = json.readValue(d.getExtracted(), LinkedHashMap.class);
        return new DocumentInfo(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getExtraction(),
                extracted, d.getStatus(), d.getDocType(), d.getNumber(), d.getDocDate(), d.getCurrency(), d.getAmount(),
                d.getCounterparty(), d.getCounterpartyTin(), d.getExpenseId(), d.getIncomeId(), d.getInvoiceId(),
                d.getBankTransactionId(), matches(d, extracted), d.getCreatedAt());
    }

    /** Банковские операции, похожие на оплату по документу: та же сумма, дата в пределах двух недель. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> matches(Document d, Map<String, Object> extracted) {
        BigDecimal amount = d.getAmount();
        LocalDate date = d.getDocDate();
        if (amount == null && extracted.get("amount") instanceof Map<?, ?> a && a.get("value") != null) {
            amount = new BigDecimal(String.valueOf(a.get("value")));
        }
        if (date == null && extracted.get("date") instanceof Map<?, ?> a && a.get("value") != null) {
            date = LocalDate.parse(String.valueOf(a.get("value")));
        }
        if (amount == null || date == null) return List.of();
        BigDecimal target = amount;
        LocalDate day = date;
        return transactions.findByBusinessIdAndDateBetweenOrderByDateDescIdDesc(d.getBusinessId(), date.minusDays(14), date.plusDays(30))
                .stream()
                .filter(t -> t.getAmountAmd() != null && t.getAmountAmd().abs().compareTo(target) == 0
                        || t.getAmount().abs().compareTo(target) == 0)
                .limit(5)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("bankTransactionId", t.getId());
                    m.put("date", t.getDate());
                    m.put("amount", t.getAmount());
                    m.put("currency", t.getCurrency());
                    m.put("counterparty", t.getCounterparty());
                    m.put("kind", t.getKind());
                    m.put("expenseId", t.getExpenseId());
                    m.put("incomeId", t.getIncomeId());
                    m.put("daysFromDocument", ChronoUnit.DAYS.between(day, t.getDate()));
                    return m;
                }).toList();
    }

    /** Тип по сигнатуре файла, а не по заявленному клиентом. */
    static String sniff(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) return "image/jpeg";
        return null;
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim(String s, int max) {
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
