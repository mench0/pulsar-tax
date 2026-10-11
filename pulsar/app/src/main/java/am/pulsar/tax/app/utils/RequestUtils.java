package am.pulsar.tax.app.utils;import am.pulsar.tax.app.exception.CustomValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Разбор параметров строки запроса. */
public final class RequestUtils {

    private RequestUtils() {
    }

    /** Год из ?year= или текущий; 2000–2100. */
    public static int year(String year) {
        if (year == null) {
            return ClockUtils.today().getYear();
        }
        try {
            int y = Integer.parseInt(year);
            if (y < 2000 || y > 2100) {
                throw new NumberFormatException();
            }
            return y;
        } catch (NumberFormatException e) {
            throw new CustomValidationException("Неверный год: " + year);
        }
    }

    /** Дата из ?date= или сегодня. */
    public static LocalDate date(String date) {
        if (date == null || date.isBlank()) {
            return ClockUtils.today();
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new CustomValidationException("Дата «date» должна быть в формате ГГГГ-ММ-ДД");
        }
    }
}
