package am.pulsar.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Чтение и проверка полей из разобранного JSON с понятными ошибками. */
final class Fields {

    /** Целых цифр не больше: 999 млрд — с запасом выше любых порогов. */
    static final int MAX_INT_DIGITS = 12;
    static final int MAX_SCALE = 4;

    private final Map<String, Object> m;

    Fields(Map<String, Object> m) {
        this.m = m;
    }

    String str(String key, int max, boolean required) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (required) throw new ApiException(400, "Заполните поле «" + key + "»");
            return "";
        }
        if (!(v instanceof String s)) throw new ApiException(400, "Поле «" + key + "» должно быть строкой");
        String t = s.trim();
        if (t.length() > max) throw new ApiException(400, "Поле «" + key + "» длиннее " + max + " символов");
        return t;
    }

    BigDecimal dec(String key, boolean required) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (required) throw new ApiException(400, "Заполните поле «" + key + "»");
            return null;
        }
        BigDecimal d = null;
        try {
            if (v instanceof BigDecimal b) d = b;
            if (v instanceof String s) d = new BigDecimal(s.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            // упадём на общей ошибке ниже
        }
        if (d == null) throw new ApiException(400, "Поле «" + key + "» должно быть числом");
        return limited(key, d);
    }

    /**
     * Ограничивает размер числа. Без этого «1e999999999» проходит разбор,
     * а при записи в JSON превращается в строку из миллиарда цифр.
     */
    private static BigDecimal limited(String key, BigDecimal d) {
        BigDecimal n = d.stripTrailingZeros();
        if (n.precision() - n.scale() > MAX_INT_DIGITS) {
            throw new ApiException(400, "Поле «" + key + "» слишком большое");
        }
        if (n.scale() > MAX_SCALE) {
            throw new ApiException(400, "Поле «" + key + "»: не больше " + MAX_SCALE + " знаков после запятой");
        }
        return n.scale() < 0 ? n.setScale(0) : n;
    }

    LocalDate date(String key, boolean required) {
        Object v = m.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (required) throw new ApiException(400, "Укажите дату «" + key + "»");
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Дата «" + key + "» должна быть в формате ГГГГ-ММ-ДД");
        }
    }

    <E extends Enum<E>> E enumValue(String key, Class<E> type) {
        Object v = m.get(key);
        if (v == null) throw new ApiException(400, "Укажите «" + key + "»");
        try {
            return Enum.valueOf(type, String.valueOf(v));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Неизвестное значение «" + v + "» для «" + key + "»");
        }
    }

    @SuppressWarnings("unchecked")
    List<Object> list(String key) {
        Object v = m.get(key);
        if (!(v instanceof List)) throw new ApiException(400, "Поле «" + key + "» должно быть списком");
        return (List<Object>) v;
    }
}
