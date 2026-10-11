package am.pulsar.tax.app.service.bank;

import am.pulsar.tax.app.service.bank.TransactionClassifier.History;
import am.pulsar.tax.app.service.bank.TransactionClassifier.Suggestion;
import am.pulsar.tax.dto.enums.TransactionKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Разбор выписки и подсказка типа операции. */
class StatementParsingTest {

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    void amounts() {
        assertEquals(d("1234.56"), StatementParser.amount("1 234,56"));
        assertEquals(d("1234.56"), StatementParser.amount("1,234.56"));
        assertEquals(d("1234.56"), StatementParser.amount("1.234,56"));
        assertEquals(d("-1234.5"), StatementParser.amount("-1234.5"));
        assertEquals(d("1234.5"), StatementParser.amount("1234,5 AMD"));
        assertEquals(d("1234567"), StatementParser.amount("1,234,567"));
        assertEquals(d("-1000"), StatementParser.amount("(1 000)"));
        assertEquals(d("150000"), StatementParser.amount("150 000"));
        assertNull(StatementParser.amount("—"));
        assertNull(StatementParser.amount(""));
        assertNull(StatementParser.amount("1-2"));
    }

    @Test
    void dates() {
        assertEquals(LocalDate.of(2026, 3, 1), StatementParser.date("01.03.2026", "dd.mm.yyyy"));
        assertEquals(LocalDate.of(2026, 3, 1), StatementParser.date("2026-03-01 10:15", "yyyy-mm-dd"));
        assertEquals(LocalDate.of(2026, 3, 1), StatementParser.date("03/01/2026", "mm/dd/yyyy"));
        assertNull(StatementParser.date("31.02.2026", "dd.mm.yyyy"));
        assertNull(StatementParser.date("2026-03-01", "dd.mm.yyyy"));
    }

    @Test
    void fingerprintsAreStableAndCountRepeats() {
        List<String> row = List.of("01.03.2026", "-500", "Комиссия");
        assertEquals(StatementParser.fingerprint(row, 0), StatementParser.fingerprint(List.of("01.03.2026 ", "-500", "Комиссия"), 0));
        assertNotEquals(StatementParser.fingerprint(row, 0), StatementParser.fingerprint(row, 1));
    }

    /** Отпечаток прежнего импорта совпадает с веб-клиентом (cyrb53): строки старого импорта узнаются как дубли. */
    @Test
    void legacyKeyMatchesWebClient() {
        assertEquals("csv-43205110451f10ca",
                StatementParser.legacyKey(List.of("01.03.2026", "150 000,50", "Оплата по счёту №5", "ООО «Ромашка»")));
        assertEquals("csv-5d0b635f74d73be2", StatementParser.legacyKey(List.of("2026-01-05", "-1200", "Комиссия", "AMD")));
    }

    @Test
    void classification() {
        Suggestion own = TransactionClassifier.classify(d("-1000"), "", "Перевод между своими счетами", null);
        assertEquals(TransactionKind.OWN_TRANSFER, own.kind());
        assertTrue(own.reasons().contains("KEYWORD_OWN_TRANSFER"));

        assertEquals(TransactionKind.BANK_FEE, TransactionClassifier.classify(d("-500"), "", "Комиссия за перевод", null).kind());
        assertEquals(TransactionKind.REFUND_RECEIVED, TransactionClassifier.classify(d("500"), "", "Возврат по покупке", null).kind());
        assertEquals(TransactionKind.REFUND_ISSUED, TransactionClassifier.classify(d("-500"), "", "Refund to customer", null).kind());
        assertEquals(TransactionKind.TAX_PAYMENT, TransactionClassifier.classify(d("-50000"), "Казначейство", "налог с оборота", null).kind());

        Suggestion invoice = TransactionClassifier.classify(d("150000"), "ООО Ромашка", "Оплата по счету 15", null);
        assertEquals(TransactionKind.INCOME, invoice.kind());
        assertTrue(invoice.confidence().compareTo(TransactionClassifier.CONFIDENT) < 0); // без истории — на проверку

        // Только знак суммы — низкая уверенность.
        Suggestion plain = TransactionClassifier.classify(d("1000"), "Иванов", "перевод", null);
        assertEquals(TransactionKind.INCOME, plain.kind());
        assertEquals(d("0.5"), plain.confidence());

        // Противоречивые признаки — «прочее», решает пользователь.
        assertEquals(TransactionKind.OTHER, TransactionClassifier.classify(d("200"), "", "Возврат комиссии, конвертация", null).kind());
        // Кредит и проценты — не доход.
        assertEquals(TransactionKind.OTHER, TransactionClassifier.classify(d("1000000"), "Банк", "Выдача кредита", null).kind());

        // История пользователя сильнее ключевых слов.
        Suggestion hist = TransactionClassifier.classify(d("1000"), "Иванов", "перевод", new History(TransactionKind.OWN_TRANSFER, 3, true));
        assertEquals(TransactionKind.OWN_TRANSFER, hist.kind());
        assertEquals(d("0.95"), hist.confidence());
        // Противоречивая история не даёт уверенности.
        Suggestion mixed = TransactionClassifier.classify(d("1000"), "Иванов", "перевод", new History(TransactionKind.INCOME, 2, false));
        assertTrue(mixed.reasons().contains("USER_HISTORY_MIXED"));
        assertEquals(d("0.5"), mixed.confidence());
        // История не применяется, если тип не подходит к направлению денег.
        assertEquals(TransactionKind.EXPENSE, TransactionClassifier.classify(d("-1000"), "Иванов", "", new History(TransactionKind.INCOME, 5, true)).kind());
    }
}
