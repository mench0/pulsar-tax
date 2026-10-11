package am.pulsar.tax.app.service.bank;

import am.pulsar.tax.dto.enums.TransactionKind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Предположение о типе банковской операции по назначению платежа, контрагенту, знаку суммы
 * и истории решений пользователя. Результат — только подсказка: в учёт операция попадает после подтверждения.
 *
 * <p>Уверенность: история пользователя (3+ одинаковых решения) — 0.95, ключевые слова — 0.8–0.9,
 * только знак суммы — 0.5. Противоречивые признаки понижают уверенность и дают тип OTHER.
 */
public final class TransactionClassifier {

    /** С какой уверенности подсказку можно подтвердить одной кнопкой «подтвердить уверенные». */
    public static final BigDecimal CONFIDENT = new BigDecimal("0.85");

    static final List<String> OWN = List.of("между своими", "свой счет", "свой счёт", "собственн", "own account",
            "between accounts", "between own", "internal transfer", "սեփական", "հաշիվների միջև", "конвертац",
            "conversion", "փոխարկում", "card to account", "пополнение карты", "перевод на карту");
    static final List<String> REFUND = List.of("возврат", "refund", "chargeback", "cashback", "кэшбэк", "кешбэк",
            "վերադարձ", "reversal", "сторно");
    static final List<String> FEE = List.of("комисси", "commission", "bank fee", "fee for", "обслуживан", "service charge",
            "միջնորդավճար", "սպասարկման վճար", "abonent", "абонент");
    static final List<String> TAX = List.of("налог", "tax", "պետական բյուջե", "հարկ", "treasury", "казначейств",
            "государственн пошлин", "пошлин", "տուրք", "900005");
    static final List<String> INCOME = List.of("оплата по счет", "оплата по договор", "invoice", "payment for",
            "за услуги", "for services", "по договору", "ծառայությունների դիմաց", "հաշիվ-ապրանքագիր", "consulting",
            "development", "salary from", "оплата за");
    static final List<String> LOAN = List.of("кредит", "loan", "заем", "заём", "վարկ", "փոխառություն", "deposit",
            "депозит", "ավանդ", "проценты по вклад", "interest");

    /** Решение пользователя по контрагенту из истории: тип и сколько раз подряд выбран. */
    public record History(TransactionKind kind, int times, boolean consistent) {
    }

    public record Suggestion(TransactionKind kind, BigDecimal confidence, List<String> reasons) {
    }

    private TransactionClassifier() {
    }

    public static Suggestion classify(BigDecimal amount, String counterparty, String description, History history) {
        List<String> reasons = new ArrayList<>();
        boolean in = amount.signum() > 0;
        reasons.add(in ? "SIGN_INCOMING" : "SIGN_OUTGOING");
        String text = StatementParser.normalize(counterparty + " " + description);

        if (history != null && history.consistent() && history.times() >= 1 && signFits(history.kind(), in)) {
            reasons.add("USER_HISTORY");
            BigDecimal c = history.times() >= 3 ? new BigDecimal("0.95") : history.times() == 2 ? new BigDecimal("0.85") : new BigDecimal("0.75");
            return new Suggestion(history.kind(), c, reasons);
        }
        if (history != null && !history.consistent()) reasons.add("USER_HISTORY_MIXED");

        Map<TransactionKind, String> hits = new EnumMap<>(TransactionKind.class);
        hit(hits, text, OWN, TransactionKind.OWN_TRANSFER);
        hit(hits, text, REFUND, in ? TransactionKind.REFUND_RECEIVED : TransactionKind.REFUND_ISSUED);
        if (!in) hit(hits, text, FEE, TransactionKind.BANK_FEE);
        if (!in) hit(hits, text, TAX, TransactionKind.TAX_PAYMENT);
        if (in) hit(hits, text, INCOME, TransactionKind.INCOME);
        boolean loan = LOAN.stream().anyMatch(text::contains);
        if (loan) reasons.add("KEYWORD_LOAN_OR_DEPOSIT");
        hits.values().forEach(reasons::add);

        if (hits.size() > 1) {
            // Например, «возврат комиссии» или «перевод на свой счёт за услуги» — решает пользователь.
            reasons.add("CONFLICTING_SIGNS");
            return new Suggestion(TransactionKind.OTHER, new BigDecimal("0.3"), reasons);
        }
        if (loan) {
            // Кредит, депозит и проценты — не выручка и не обычный расход: нужна ручная проверка.
            return new Suggestion(TransactionKind.OTHER, new BigDecimal("0.4"), reasons);
        }
        if (hits.size() == 1) {
            TransactionKind k = hits.keySet().iterator().next();
            BigDecimal c = k == TransactionKind.INCOME ? new BigDecimal("0.8") : new BigDecimal("0.9");
            return new Suggestion(k, c, reasons);
        }
        return new Suggestion(in ? TransactionKind.INCOME : TransactionKind.EXPENSE, new BigDecimal("0.5"), reasons);
    }

    /** Подходит ли тип к направлению денег. */
    public static boolean signFits(TransactionKind kind, boolean incoming) {
        return switch (kind) {
            case INCOME, REFUND_RECEIVED -> incoming;
            case EXPENSE, REFUND_ISSUED, BANK_FEE, TAX_PAYMENT -> !incoming;
            case OWN_TRANSFER, OTHER -> true;
        };
    }

    private static void hit(Map<TransactionKind, String> hits, String text, List<String> words, TransactionKind kind) {
        for (String w : words) {
            if (text.contains(w)) {
                hits.put(kind, "KEYWORD_" + kind);
                return;
            }
        }
    }
}
