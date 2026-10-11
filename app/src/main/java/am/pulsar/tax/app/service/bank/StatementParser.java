package am.pulsar.tax.app.service.bank;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор значений выписки: суммы в разных записях, даты в трёх форматах и отпечатки строк. */
public final class StatementParser {

    public static final List<String> DATE_FORMATS = List.of("dd.mm.yyyy", "yyyy-mm-dd", "mm/dd/yyyy");

    private static final Pattern YMD = Pattern.compile("^(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern COMMA_DECIMAL = Pattern.compile(",\\d{1,2}$");

    private StatementParser() {
    }

    /** Число из выписки: «1 234,56», «1,234.56», «-1234.5», «1234,5 AMD», «(1 000)» — минус. null, если не число. */
    public static BigDecimal amount(String raw) {
        if (raw == null) return null;
        String t = raw.strip();
        boolean parens = t.startsWith("(") && t.endsWith(")");
        String s = t.replaceAll("[\\s\\u00a0\\u202f']", "").replaceAll("[^\\d,.\\-+]", "");
        if (s.isEmpty()) return null;
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            s = lastComma > lastDot ? s.replace(".", "").replaceFirst(",", ".") : s.replace(",", "");
        } else if (lastComma >= 0) {
            s = COMMA_DECIMAL.matcher(s).find() && s.split(",", -1).length == 2 ? s.replaceFirst(",", ".") : s.replace(",", "");
        }
        try {
            BigDecimal v = new BigDecimal(s);
            return parens ? v.abs().negate() : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Дата в указанном формате или null (в том числе для несуществующих дат вроде 31.02). */
    public static LocalDate date(String raw, String format) {
        if (raw == null) return null;
        Matcher m = ("yyyy-mm-dd".equals(format) ? YMD : DMY).matcher(raw.strip());
        if (!m.find()) return null;
        String y, mo, d;
        switch (format == null ? "" : format) {
            case "yyyy-mm-dd" -> { y = m.group(1); mo = m.group(2); d = m.group(3); }
            case "mm/dd/yyyy" -> { y = m.group(3); mo = m.group(1); d = m.group(2); }
            default -> { y = m.group(3); mo = m.group(2); d = m.group(1); }
        }
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Ячейка строки или null, если колонки нет. */
    public static String cell(List<String> row, int col) {
        return col >= 0 && row != null && col < row.size() ? row.get(col) : null;
    }

    /** Отпечаток строки: SHA-256 от ячеек и номера повтора такой же строки в файле. */
    public static String fingerprint(List<String> row, int occurrence) {
        String joined = String.join("\u001f", row.stream().map(c -> c == null ? "" : c.strip()).toList()) + "\u001e" + occurrence;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Отпечаток в формате прежнего импорта доходов (cyrb53 от ячеек через «|», как в веб-клиенте).
     * По нему узнаются строки, уже загруженные старым способом в incomes.import_key.
     */
    public static String legacyKey(List<String> row) {
        String str = String.join("|", row);
        int h1 = 0xdeadbeef;
        int h2 = 0x41c6ce57;
        for (int i = 0; i < str.length(); i++) {
            int ch = str.charAt(i);
            h1 = (h1 ^ ch) * (int) 2654435761L;
            h2 = (h2 ^ ch) * 1597334677;
        }
        h1 = (h1 ^ (h1 >>> 16)) * ((int) 2246822507L) ^ (h2 ^ (h2 >>> 13)) * ((int) 3266489909L);
        h2 = (h2 ^ (h2 >>> 16)) * ((int) 2246822507L) ^ (h1 ^ (h1 >>> 13)) * ((int) 3266489909L);
        return "csv-" + Integer.toHexString(h2) + Integer.toHexString(h1);
    }

    /** Текст для сравнения: нижний регистр, без лишних пробелов и кавычек. */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[\"«»“”'`]", "").replaceAll("\\s+", " ").strip();
    }
}
