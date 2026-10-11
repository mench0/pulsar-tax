package am.pulsar.tax.app.service;

import am.pulsar.tax.app.entity.BankTransaction;
import am.pulsar.tax.app.entity.Income;
import am.pulsar.tax.app.entity.Invoice;
import am.pulsar.tax.app.entity.InvoicePayment;
import am.pulsar.tax.app.exception.CustomValidationException;
import am.pulsar.tax.app.exception.EntityNotFoundByIdException;
import am.pulsar.tax.app.repository.BankTransactionRepository;
import am.pulsar.tax.app.repository.IncomeRepository;
import am.pulsar.tax.app.repository.InvoicePaymentRepository;
import am.pulsar.tax.app.repository.InvoiceRepository;
import am.pulsar.tax.app.service.bank.StatementParser;
import am.pulsar.tax.dto.enums.InvoiceStatus;
import am.pulsar.tax.dto.enums.TransactionKind;
import am.pulsar.tax.dto.enums.TransactionStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сверка счетов с поступлениями. Доход (уже учтённый в обороте) распределяется по счетам — сам доход
 * при этом не дублируется: сверка не создаёт новых доходов, а только связывает существующие.
 * Сумма распределений одного дохода не может превышать сам доход.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReconciliationService {

    /** С какой оценки показывать подсказку сопоставления. */
    static final double MIN_SCORE = 0.5;

    InvoiceRepository invoices;
    IncomeRepository incomes;
    InvoicePaymentRepository payments;
    BankTransactionRepository transactions;

    public record InvoiceState(Long id, String number, String client, LocalDate date, LocalDate dueDate,
                               BigDecimal total, BigDecimal paid, BigDecimal refunded, BigDecimal balance,
                               String state, boolean overdue, List<InvoicePayment> payments) {
    }

    public record IncomeState(Long id, LocalDate date, BigDecimal amount, BigDecimal allocated, BigDecimal remaining,
                              String client, String description) {
    }

    public record Suggestion(Long invoiceId, Long incomeId, BigDecimal amount, double score, List<String> reasons) {
    }

    public record Overview(List<InvoiceState> invoices, List<IncomeState> unmatchedIncomes,
                           List<Suggestion> suggestions, BigDecimal receivable, BigDecimal overdue) {
    }

    @Transactional(readOnly = true)
    public Overview overview(Long businessId, LocalDate today) {
        List<Invoice> invs = invoices.findByBusinessIdOrderByDateAscIdAsc(businessId);
        List<InvoicePayment> all = payments.findByBusinessId(businessId);
        Map<Long, List<InvoicePayment>> byInvoice = new HashMap<>();
        Map<Long, BigDecimal> byIncome = new HashMap<>();
        for (InvoicePayment p : all) {
            byInvoice.computeIfAbsent(p.getInvoiceId(), k -> new ArrayList<>()).add(p);
            if (p.getIncomeId() != null) byIncome.merge(p.getIncomeId(), p.getAmount(), BigDecimal::add);
        }
        List<InvoiceState> states = new ArrayList<>();
        BigDecimal receivable = BigDecimal.ZERO, overdueSum = BigDecimal.ZERO;
        for (Invoice i : invs) {
            InvoiceState s = state(i, byInvoice.getOrDefault(i.getId(), List.of()), today);
            states.add(s);
            if (s.balance().signum() > 0) {
                receivable = receivable.add(s.balance());
                if (s.overdue()) overdueSum = overdueSum.add(s.balance());
            }
        }
        List<IncomeState> unmatched = new ArrayList<>();
        for (Income in : incomes.findByBusinessIdOrderByDateAscIdAsc(businessId)) {
            BigDecimal allocated = byIncome.getOrDefault(in.getId(), BigDecimal.ZERO);
            BigDecimal remaining = in.getAmount().subtract(allocated);
            if (remaining.signum() > 0) {
                unmatched.add(new IncomeState(in.getId(), in.getDate(), in.getAmount(), allocated, remaining,
                        in.getClient(), in.getDescription()));
            }
        }
        List<Suggestion> suggestions = new ArrayList<>();
        Map<String, Invoice> invoiceById = new HashMap<>();
        for (Invoice i : invs) invoiceById.put(String.valueOf(i.getId()), i);
        for (InvoiceState s : states) {
            if (s.balance().signum() <= 0) continue;
            Invoice inv = invoiceById.get(String.valueOf(s.id()));
            Suggestion best = null;
            for (IncomeState in : unmatched) {
                Suggestion sg = score(inv, s.balance(), in);
                if (sg != null && (best == null || sg.score() > best.score())) best = sg;
            }
            if (best != null) suggestions.add(best);
        }
        suggestions.sort(Comparator.comparingDouble(Suggestion::score).reversed());
        return new Overview(states, unmatched, suggestions, receivable, overdueSum);
    }

    /** Насколько поступление похоже на оплату счёта: сумма, номер в назначении, клиент, дата. */
    static Suggestion score(Invoice inv, BigDecimal balance, IncomeState in) {
        List<String> reasons = new ArrayList<>();
        double score = 0;
        if (in.remaining().compareTo(balance) == 0) {
            score += 0.5;
            reasons.add("AMOUNT_EQUALS_BALANCE");
        } else if (in.remaining().compareTo(balance) < 0) {
            score += 0.1;
            reasons.add("PARTIAL_AMOUNT");
        } else {
            reasons.add("AMOUNT_EXCEEDS_BALANCE");
        }
        String text = StatementParser.normalize(in.client() + " " + in.description());
        String number = StatementParser.normalize(inv.getNumber());
        if (!number.isEmpty() && text.contains(number)) {
            score += 0.4;
            reasons.add("NUMBER_IN_DESCRIPTION");
        }
        String client = StatementParser.normalize(inv.getClient());
        if (!client.isEmpty() && (text.contains(client) || (!StatementParser.normalize(in.client()).isEmpty()
                && client.contains(StatementParser.normalize(in.client()))))) {
            score += 0.25;
            reasons.add("CLIENT_MATCHES");
        }
        long days = ChronoUnit.DAYS.between(inv.getDate(), in.date());
        if (days < -3) {
            score -= 0.3;
            reasons.add("PAID_BEFORE_INVOICE");
        } else if (days <= 90) {
            score += 0.1;
            reasons.add("DATE_PLAUSIBLE");
        }
        if (score < MIN_SCORE) return null;
        return new Suggestion(inv.getId(), in.id(), in.remaining().min(balance), Math.min(1, score), reasons);
    }

    static InvoiceState state(Invoice i, List<InvoicePayment> ps, LocalDate today) {
        BigDecimal total = i.total();
        BigDecimal paid = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
        for (InvoicePayment p : ps) {
            if (p.getAmount().signum() > 0) paid = paid.add(p.getAmount());
            else refunded = refunded.add(p.getAmount().negate());
        }
        BigDecimal net = paid.subtract(refunded);
        BigDecimal balance = total.subtract(net);
        String st = net.signum() <= 0 ? "OPEN" : balance.signum() > 0 ? "PARTIAL" : balance.signum() == 0 ? "PAID" : "OVERPAID";
        boolean overdue = balance.signum() > 0 && i.getDueDate() != null && i.getDueDate().isBefore(today);
        return new InvoiceState(i.getId(), i.getNumber(), i.getClient(), i.getDate(), i.getDueDate(), total, paid, refunded,
                balance, st, overdue, ps);
    }

    /**
     * Связывает доход со счётом. amount по умолчанию — сколько осталось и у дохода, и у счёта.
     * Переплату по счёту можно отметить явно (amount больше остатка), но не больше нераспределённой части дохода.
     */
    @Transactional
    public InvoicePayment allocate(Long businessId, Long invoiceId, Long incomeId, BigDecimal amount, String source) {
        Invoice inv = invoices.findById(invoiceId).filter(i -> i.getBusinessId().equals(businessId))
                .orElseThrow(() -> new EntityNotFoundByIdException(Invoice.class, invoiceId));
        Income in = incomes.findById(incomeId).filter(i -> i.getBusinessId().equals(businessId))
                .orElseThrow(() -> new EntityNotFoundByIdException(Income.class, incomeId));
        List<InvoicePayment> forIncome = payments.findByIncomeIdIn(List.of(incomeId));
        if (forIncome.stream().anyMatch(p -> p.getInvoiceId().equals(invoiceId))) {
            throw new CustomValidationException("Этот доход уже связан с этим счётом");
        }
        BigDecimal used = forIncome.stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal free = in.getAmount().subtract(used);
        if (free.signum() <= 0) throw new CustomValidationException("Доход уже полностью распределён по счетам");
        InvoiceState s = state(inv, payments.findByInvoiceId(invoiceId), LocalDate.now());
        BigDecimal value = amount != null ? amount : free.min(s.balance().max(BigDecimal.ZERO));
        if (value.signum() <= 0) throw new CustomValidationException("Счёт уже оплачен полностью");
        if (value.compareTo(free) > 0) {
            throw new CustomValidationException("Нельзя распределить больше, чем осталось от дохода: " + free.toPlainString());
        }
        InvoicePayment p = payments.save(InvoicePayment.builder().businessId(businessId).invoiceId(invoiceId)
                .incomeId(incomeId).amount(value).source(source == null ? "MANUAL" : source).build());
        syncStatus(inv);
        return p;
    }

    /** Возврат покупателю по счёту: подтверждённое списание типа REFUND_ISSUED уменьшает оплату. */
    @Transactional
    public InvoicePayment allocateRefund(Long businessId, Long invoiceId, Long transactionId) {
        Invoice inv = invoices.findById(invoiceId).filter(i -> i.getBusinessId().equals(businessId))
                .orElseThrow(() -> new EntityNotFoundByIdException(Invoice.class, invoiceId));
        BankTransaction t = transactions.findByIdAndBusinessId(transactionId, businessId)
                .orElseThrow(() -> new EntityNotFoundByIdException(BankTransaction.class, transactionId));
        if (t.getStatus() != TransactionStatus.CONFIRMED || t.getKind() != TransactionKind.REFUND_ISSUED || t.getAmountAmd() == null) {
            throw new CustomValidationException("Связать со счётом можно только подтверждённый возврат покупателю");
        }
        InvoicePayment p = payments.save(InvoicePayment.builder().businessId(businessId).invoiceId(invoiceId)
                .bankTransactionId(transactionId).amount(t.getAmountAmd().abs().negate()).source("MANUAL").build());
        syncStatus(inv);
        return p;
    }

    @Transactional
    public void remove(Long businessId, Long paymentId) {
        InvoicePayment p = payments.findByIdAndBusinessId(paymentId, businessId)
                .orElseThrow(() -> new EntityNotFoundByIdException(InvoicePayment.class, paymentId));
        payments.delete(p);
        payments.flush();
        invoices.findById(p.getInvoiceId()).ifPresent(this::syncStatus);
    }

    /** Доходы удаляются: их распределения по счетам снимаются, статусы счетов пересчитываются. */
    @Transactional
    public void detachIncomes(List<Long> incomeIds) {
        if (incomeIds.isEmpty()) return;
        List<InvoicePayment> ps = payments.findByIncomeIdIn(incomeIds);
        payments.deleteAll(ps);
        payments.flush();
        ps.stream().map(InvoicePayment::getInvoiceId).distinct()
                .forEach(id -> invoices.findById(id).ifPresent(this::syncStatus));
    }

    /** Статус счёта PAID — когда оплата покрывает сумму; иначе SENT. */
    void syncStatus(Invoice inv) {
        InvoiceState s = state(inv, payments.findByInvoiceId(inv.getId()), LocalDate.now());
        InvoiceStatus st = s.balance().signum() <= 0 && s.paid().signum() > 0 ? InvoiceStatus.PAID : InvoiceStatus.SENT;
        if (inv.getStatus() != st) {
            inv.setStatus(st);
            invoices.save(inv);
        }
    }

    /**
     * Поступление, которое уже учтено и похоже на оплату счёта (та же сумма, номер или клиент).
     * Используется, чтобы «Счёт оплачен» не создал второй доход за те же деньги.
     */
    @Transactional(readOnly = true)
    public IncomeState likelyExistingPayment(Long businessId, Invoice inv) {
        Overview o = overview(businessId, LocalDate.now());
        InvoiceState s = o.invoices().stream().filter(x -> Objects.equals(x.id(), inv.getId())).findFirst().orElse(null);
        if (s == null || s.balance().signum() <= 0) return null;
        return o.unmatchedIncomes().stream()
                .map(in -> new Object[]{in, score(inv, s.balance(), in)})
                .filter(a -> a[1] != null && ((Suggestion) a[1]).reasons().contains("AMOUNT_EQUALS_BALANCE")
                        && ((Suggestion) a[1]).score() >= 0.6)
                .map(a -> (IncomeState) a[0])
                .findFirst().orElse(null);
    }
}
