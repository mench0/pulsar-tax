package am.pulsar.tax.app.service.document;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Реквизиты из текста документа и честная уверенность при неоднозначности. */
class DocumentFieldsTest {

    @Test
    void russianInvoice() {
        String text = """
                ООО «Ромашка», ИНН 01234567
                Счёт № 2026/15 от 12.03.2026
                Услуги хостинга          10 000,00
                Поддержка                 2 500,00
                Итого к оплате: 12 500,00 драм
                """;
        Map<String, DocumentFields.Field> f = DocumentFields.extract(text);
        assertEquals("2026/15", f.get("number").value());
        assertEquals(LocalDate.of(2026, 3, 12), f.get("date").value());
        assertEquals(new BigDecimal("12500.00"), f.get("amount").value());
        assertTrue(f.get("amount").confidence() >= 0.8);
        assertEquals("AMD", f.get("currency").value());
        assertEquals("01234567", f.get("counterpartyTin").value());
        assertEquals("ООО «Ромашка»", f.get("counterparty").value());
    }

    @Test
    void armenianAndEnglish() {
        Map<String, DocumentFields.Field> hy = DocumentFields.extract("ՍՊԸ Արև ՀՎՀՀ 02345678\nՀաշիվ № A-17\n2026-04-01\nԸնդամենը 45 000 ֏");
        assertEquals("A-17", hy.get("number").value());
        assertEquals(new BigDecimal("45000"), hy.get("amount").value());
        assertEquals("02345678", hy.get("counterpartyTin").value());
        Map<String, DocumentFields.Field> en = DocumentFields.extract("Invoice #INV-9\nDate: 05/02/2026\nTotal due: $1,250.50");
        assertEquals("INV-9", en.get("number").value());
        assertEquals(new BigDecimal("1250.50"), en.get("amount").value());
        assertEquals("USD", en.get("currency").value());
    }

    @Test
    void ambiguityLowersConfidence() {
        Map<String, DocumentFields.Field> f = DocumentFields.extract("Чек\n01.03.2026\n02.03.2026\nоплачено 1 000,00 и 2 000,00");
        assertTrue(f.get("date").confidence() < 0.5); // две разные даты
        assertEquals(1, f.get("date").alternatives().size());
        assertTrue(f.get("amount").confidence() < 0.5); // нет строки «итого» — только догадка
        assertTrue(DocumentFields.extract("").isEmpty());
    }
}
