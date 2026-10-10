package am.pulsar.tax;

import am.pulsar.tax.Model.Income;
import am.pulsar.tax.Model.Invoice;

import java.util.List;

/** Выгрузка в CSV для бухгалтера или Excel: UTF-8 с BOM, разделитель «;». */
final class Csv {

    private Csv() {
    }

    static String incomes(List<Income> list) {
        StringBuilder sb = new StringBuilder("﻿");
        row(sb, "date", "amount_amd", "currency", "original_amount", "rate", "client", "description", "invoice_id");
        for (Income i : list) {
            row(sb, i.date, i.amount, i.currency, i.originalAmount, i.rate, i.client, i.description, i.invoiceId);
        }
        return sb.toString();
    }

    static String invoices(List<Invoice> list) {
        StringBuilder sb = new StringBuilder("﻿");
        row(sb, "number", "date", "due_date", "client", "client_tin", "total_amd", "status", "e_invoice_number");
        for (Invoice i : list) {
            row(sb, i.number, i.date, i.dueDate, i.client, i.clientTin, i.total(), i.status, i.eInvoiceNumber);
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
        String s = v instanceof java.math.BigDecimal d ? d.toPlainString() : String.valueOf(v);
        // Формулы из текста клиента не должны выполняться в Excel.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
