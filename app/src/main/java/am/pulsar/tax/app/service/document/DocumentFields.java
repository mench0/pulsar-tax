package am.pulsar.tax.app.service.document;

import am.pulsar.tax.app.service.bank.StatementParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Реквизиты из текста документа: номер, дата, сумма, валюта, контрагент, ИНН (ՀՎՀՀ).
 * Каждое значение — только предположение с уверенностью и фрагментом текста, откуда оно взято.
 * Если кандидатов несколько и они расходятся, уверенность понижается и это видно пользователю.
 */
public final class DocumentFields {

    public record Field(Object value, double confidence, String snippet, List<Object> alternatives) {
    }

    private static final Pattern NUMBER = Pattern.compile(
            "(?iu)(?:сч[её]т(?:-фактура)?|сч\\.|сф|invoice|inv\\.?|акт|чек|receipt|հաշիվ(?:-ապրանքագիր)?|ակտ|կտրոն)\\s*(?:№|no\\.?|#|n\\s|номер|համար)?\\s*[:№#]?\\s*([A-Za-zА-Яа-я0-9][A-Za-zА-Яа-я0-9/\\-_.]{0,30})");
    private static final Pattern NUMBER_SIGN = Pattern.compile("(?u)(?:№|No\\.?|N°)\\s*([A-Za-zА-Яа-я0-9][A-Za-zА-Яа-я0-9/\\-_.]{0,30})");
    private static final Pattern DATE_DMY = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(?!\\d)");
    private static final Pattern DATE_YMD = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern TOTAL = Pattern.compile(
            "(?iu)(итого(?:\\s+к\\s+оплате)?|всего(?:\\s+к\\s+оплате)?|к\\s+оплате|сумма|total(?:\\s+due)?|amount\\s+due|grand\\s+total|ընդամենը|վճարման\\s+ենթակա|գումար)[^\\d\\n]{0,25}([\\d][\\d\\s\\u00a0.,']{0,20}\\d|\\d)");
    private static final Pattern MONEY = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:[\\s\\u00a0.,']\\d{3})+(?:[.,]\\d{1,2})?|\\d+[.,]\\d{2})(?![\\d])");
    private static final Pattern TIN = Pattern.compile("(?iu)(?:ИНН|ՀՎՀՀ|TIN|УНН|tax\\s*id)\\s*[:№]?\\s*(\\d{8})(?!\\d)");
    private static final Pattern PARTY = Pattern.compile(
            "(?mU)^([^\\n]{0,80}\\b(?:ООО|ОАО|ЗАО|АО|ИП|LLC|CJSC|OJSC|LTD|Ltd|ՍՊԸ|ՓԲԸ|ԲԲԸ|ԱՁ)\\b[^\\n]{0,80})$");
    private static final Pattern PARTY_TAIL = Pattern.compile("(?iU)[,;]?\\s*(?:ИНН|ՀՎՀՀ|TIN|УНН|tax\\s*id)\\b.*$");

    private DocumentFields() {
    }

    public static Map<String, Field> extract(String text) {
        Map<String, Field> m = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return m;

        List<Object> numbers = new ArrayList<>();
        String numberSnippet = null;
        for (Pattern p : List.of(NUMBER, NUMBER_SIGN)) {
            Matcher x = p.matcher(text);
            while (x.find()) {
                String v = x.group(1).replaceAll("[.]+$", "");
                if (v.matches("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}")) continue; // это дата
                if (!numbers.contains(v)) {
                    numbers.add(v);
                    if (numberSnippet == null) numberSnippet = snippet(text, x.start(), x.end());
                }
            }
            if (!numbers.isEmpty()) break;
        }
        if (!numbers.isEmpty()) m.put("number", field(numbers, numberSnippet, 0.7));

        List<Object> dates = new ArrayList<>();
        String dateSnippet = null;
        Matcher d = DATE_DMY.matcher(text);
        while (d.find()) {
            LocalDate v = StatementParser.date(d.group(), "dd.mm.yyyy");
            if (v != null && !dates.contains(v)) {
                dates.add(v);
                if (dateSnippet == null) dateSnippet = snippet(text, d.start(), d.end());
            }
        }
        Matcher dy = DATE_YMD.matcher(text);
        while (dy.find()) {
            LocalDate v = StatementParser.date(dy.group(), "yyyy-mm-dd");
            if (v != null && !dates.contains(v)) {
                dates.add(v);
                if (dateSnippet == null) dateSnippet = snippet(text, dy.start(), dy.end());
            }
        }
        if (!dates.isEmpty()) m.put("date", field(dates, dateSnippet, 0.75));

        // Сумма: строка «итого/всего/total» надёжнее, иначе — самое крупное число с копейками или разрядами.
        List<Object> totals = new ArrayList<>();
        String totalSnippet = null;
        Matcher t = TOTAL.matcher(text);
        while (t.find()) {
            BigDecimal v = StatementParser.amount(t.group(2));
            if (v != null && v.signum() > 0 && !totals.contains(v)) {
                totals.add(v);
                if (totalSnippet == null) totalSnippet = snippet(text, t.start(), t.end());
            }
        }
        if (!totals.isEmpty()) {
            // Последнее «итого» обычно и есть сумма к оплате.
            Object last = totals.getLast();
            List<Object> ordered = new ArrayList<>(totals);
            ordered.remove(last);
            ordered.addFirst(last);
            m.put("amount", field(ordered, totalSnippet, 0.8));
        } else {
            BigDecimal max = null;
            String snip = null;
            Matcher mo = MONEY.matcher(text);
            while (mo.find()) {
                BigDecimal v = StatementParser.amount(mo.group(1));
                if (v != null && (max == null || v.compareTo(max) > 0)) {
                    max = v;
                    snip = snippet(text, mo.start(), mo.end());
                }
            }
            if (max != null) m.put("amount", new Field(max, 0.35, snip, List.of()));
        }

        String upper = text.toUpperCase();
        List<Object> currencies = new ArrayList<>();
        for (String[] c : new String[][]{{"AMD", "AMD|֏|ДРАМ|ԴՐԱՄ"}, {"USD", "USD|\\$|ДОЛЛ"}, {"EUR", "EUR|€|ЕВРО"},
                {"RUB", "RUB|₽|РУБ"}, {"GBP", "GBP|£"}, {"GEL", "GEL|₾|ЛАРИ"}}) {
            if (Pattern.compile("(?u)(?<![A-ZА-Я])(" + c[1] + ")").matcher(upper).find()) currencies.add(c[0]);
        }
        if (!currencies.isEmpty()) m.put("currency", field(currencies, null, 0.7));

        Matcher tin = TIN.matcher(text);
        List<Object> tins = new ArrayList<>();
        String tinSnippet = null;
        while (tin.find()) {
            if (!tins.contains(tin.group(1))) {
                tins.add(tin.group(1));
                if (tinSnippet == null) tinSnippet = snippet(text, tin.start(), tin.end());
            }
        }
        if (!tins.isEmpty()) m.put("counterpartyTin", field(tins, tinSnippet, 0.85));

        Matcher party = PARTY.matcher(text);
        List<Object> parties = new ArrayList<>();
        while (party.find()) {
            String v = PARTY_TAIL.matcher(party.group(1)).replaceAll("").strip().replaceAll("[,;]+$", "");
            if (v.length() > 3 && !parties.contains(v)) parties.add(v);
        }
        if (!parties.isEmpty()) m.put("counterparty", field(parties, null, 0.5));
        return m;
    }

    /** Один кандидат — исходная уверенность; несколько разных — ниже, остальные в alternatives. */
    private static Field field(List<Object> values, String snippet, double base) {
        double c = values.size() == 1 ? base : Math.max(0.2, base - 0.3);
        return new Field(values.getFirst(), c, snippet, values.size() > 1 ? List.copyOf(values.subList(1, Math.min(values.size(), 5))) : List.of());
    }

    private static String snippet(String text, int start, int end) {
        int a = Math.max(0, start - 20);
        int b = Math.min(text.length(), end + 20);
        return text.substring(a, b).replaceAll("\\s+", " ").strip();
    }
}
