package am.pulsar.tax.shared;

import java.time.LocalDate;
import java.util.Map;

/** Разбор параметров строки запроса. */
public final class Requests {

    private Requests() {
    }

    /** Год из ?year= или текущий; 2000–2100. */
    public static int year(String y, LocalDate today) {
        if (y == null) return today.getYear();
        try {
            int year = Integer.parseInt(y);
            if (year < 2000 || year > 2100) throw new NumberFormatException();
            return year;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Неверный год: " + y);
        }
    }

    /** Дата из ?date= или сегодня. */
    public static LocalDate date(String d, LocalDate today) {
        return d == null ? today : new Fields(Map.of("date", d)).date("date", true);
    }

    public static String str(Object v) {
        return v instanceof String s ? s : "";
    }
}
