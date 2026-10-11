package am.pulsar.tax.domain;

import am.pulsar.tax.entity.Expense;
import am.pulsar.tax.entity.Income;
import am.pulsar.tax.entity.Invoice;

import java.util.List;

/** Выгрузка в CSV для бухгалтера или Excel: UTF-8 с BOM, разделитель «;». */
public final class Csv {

    private Csv() {
    }

    public static String incomes(List<Income> list) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        row(sb, "date", "amount_amd", "currency", "original_amount", "rate", "client", "description", "invoice_id");
        for (Income i : list) {
            row(sb, i.getDate(), i.getAmount(), i.getCurrency(), i.getOriginalAmount(), i.getRate(), i.getClient(),
                    i.getDescription(), i.getInvoiceId());
        }
        return sb.toString();
    }

    public static String invoices(List<Invoice> list) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        row(sb, "number", "date", "due_date", "client", "client_tin", "total_amd", "status", "e_invoice_number");
        for (Invoice i : list) {
            row(sb, i.getNumber(), i.getDate(), i.getDueDate(), i.getClient(), i.getClientTin(), i.total(), i.getStatus(),
                    i.getEInvoiceNumber());
        }
        return sb.toString();
    }

    public static String expenses(List<Expense> list) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        row(sb, "date", "amount_amd", "category", "description");
        for (Expense e : list) {
            row(sb, e.getDate(), e.getAmount(), e.getCategory(), e.getDescription());
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(cell(cells[i]));
        }
        sb.append("\r\n");
    }

    private static String cell(Object v) {
        if (v == null) return "";
        String s = v instanceof java.math.BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(v);
        // Формулы из текста клиента не должны выполняться в Excel.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
