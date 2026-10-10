package am.pulsar.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Чтение и проверка полей из разобранного JSON с понятными ошибками. */
final class Fields {

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
        try {
            if (v instanceof BigDecimal d) return d;
            if (v instanceof String s) return new BigDecimal(s.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            // упадём на общей ошибке ниже
        }
        throw new ApiException(400, "Поле «" + key + "» должно быть числом");
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
