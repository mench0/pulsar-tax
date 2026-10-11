package am.pulsar.tax.util;

import am.pulsar.tax.exception.ApiException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Чтение и проверка полей из тела запроса (JSON → Map) с понятными ошибками на русском.
 * Числа принимаются и числом, и строкой («150 000» из формы — нет, «150000» и «1,5» — да).
 */
public final class Fields {

    /** Целых цифр не больше: 999 млрд — с запасом выше любых порогов. */
    public static final int MAX_INT_DIGITS = 12;
    public static final int MAX_SCALE = 4;

    private final Map<String, ?> m;

    public Fields(Map<String, ?> m) {
        this.m = m;
    }

    public String str(String key, int max, boolean required) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (required) throw ApiException.badRequest("Заполните поле «" + key + "»");
            return "";
        }
        if (!(v instanceof String s)) throw ApiException.badRequest("Поле «" + key + "» должно быть строкой");
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest("Поле «" + key + "» длиннее " + max + " символов");
        return t;
    }

    public BigDecimal dec(String key, boolean required) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (required) throw ApiException.badRequest("Заполните поле «" + key + "»");
            return null;
        }
        BigDecimal d = null;
        try {
            if (v instanceof BigDecimal b) d = b;
            else if (v instanceof BigInteger b) d = new BigDecimal(b);
            else if (v instanceof Integer || v instanceof Long || v instanceof Short) d = BigDecimal.valueOf(((Number) v).longValue());
            else if (v instanceof Double || v instanceof Float) d = new BigDecimal(v.toString());
            else if (v instanceof String s) d = new BigDecimal(s.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            // упадём на общей ошибке ниже
        }
        if (d == null) throw ApiException.badRequest("Поле «" + key + "» должно быть числом");
        return limited(key, d);
    }

    /**
     * Ограничивает размер числа. Без этого «1e999999999» проходит разбор,
     * а при выводе превращается в строку из миллиарда цифр.
     */
    private static BigDecimal limited(String key, BigDecimal d) {
        BigDecimal n = d.stripTrailingZeros();
        if (n.precision() - n.scale() > MAX_INT_DIGITS) {
            throw ApiException.badRequest("Поле «" + key + "» слишком большое");
        }
        if (n.scale() > MAX_SCALE) {
            throw ApiException.badRequest("Поле «" + key + "»: не больше " + MAX_SCALE + " знаков после запятой");
        }
        return n.scale() < 0 ? n.setScale(0) : n;
    }

    /** Целое число в пределах. */
    public int intIn(String key, int min, int max, String message) {
        BigDecimal d = dec(key, true);
        if (d.scale() > 0 || d.compareTo(BigDecimal.valueOf(min)) < 0 || d.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw ApiException.badRequest(message);
        }
        return d.intValue();
    }

    public LocalDate date(String key, boolean required) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (required) throw ApiException.badRequest("Укажите дату «" + key + "»");
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Дата «" + key + "» должна быть в формате ГГГГ-ММ-ДД");
        }
    }

    public <E extends Enum<E>> E enumValue(String key, Class<E> type) {
        Object v = m.get(key);
        if (v == null) throw ApiException.badRequest("Укажите «" + key + "»");
        try {
            return Enum.valueOf(type, String.valueOf(v));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Неизвестное значение «" + v + "» для «" + key + "»");
        }
    }

    /** Значение перечисления или значение по умолчанию, если поля нет. */
    public <E extends Enum<E>> E enumOr(String key, Class<E> type, E def) {
        return m.get(key) == null ? def : enumValue(key, type);
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(String key) {
        Object v = m.get(key);
        if (!(v instanceof List)) throw ApiException.badRequest("Поле «" + key + "» должно быть списком");
        return (List<Object>) v;
    }
}
